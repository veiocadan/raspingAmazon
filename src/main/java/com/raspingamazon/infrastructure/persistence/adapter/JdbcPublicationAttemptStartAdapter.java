package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.outbox.PublicationAttemptHandle;
import com.raspingamazon.application.publication.outbox.PublicationAttemptStatus;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.port.PublicationAttemptStartPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Adapter PostgreSQL responsável por estabelecer a barreira durável
 * anterior à chamada externa de PublicationChannel.
 *
 * <p>O protocolo é:</p>
 *
 * <pre>
 * publication_outbox PROCESSING
 *         ↓
 * validar worker proprietário
 *         ↓
 * calcular attempt_number
 *         ↓
 * INSERT publication_attempt STARTED
 *         ↓
 * COMMIT
 *         ↓
 * retornar PublicationAttemptHandle
 * </pre>
 *
 * <p>Importante: este adapter exige {@code autoCommit=true} na
 * Connection recebida.</p>
 *
 * <p>Essa exigência é intencional. O JdbcTransactionAdapter suporta
 * execução dentro de transações externas usando savepoint, mas um
 * savepoint não é uma confirmação durável independente. Para uma
 * operação que precede um efeito externo, retornar antes do commit da
 * transação externa permitiria:</p>
 *
 * <pre>
 * INSERT STARTED
 *     ↓
 * savepoint liberado
 *     ↓
 * HTTP externo
 *     ↓
 * crash
 *     ↓
 * transação externa nunca commitada
 * </pre>
 *
 * <p>Nesse cenário o provider poderia receber a mensagem sem que
 * STARTED existisse depois do restart. Portanto esta fronteira não
 * aceita participação em transação externa.</p>
 */
public final class JdbcPublicationAttemptStartAdapter
    implements PublicationAttemptStartPort {

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    public JdbcPublicationAttemptStartAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        this.transactionAdapter =
            new JdbcTransactionAdapter(
                connection
            );
    }

    @Override
    public PublicationAttemptHandle start(
        long outboxId,
        String workerId,
        OffsetDateTime startedAt
    ) {

        if (outboxId <= 0L) {

            throw new IllegalArgumentException(
                "outboxId must be positive"
            );
        }

        String validatedWorkerId =
            requireText(
                workerId,
                "workerId"
            );

        Objects.requireNonNull(
            startedAt,
            "startedAt must not be null"
        );

        requireIndependentCommitBoundary();

        return transactionAdapter.execute(
            () ->
                startInsideTransaction(
                    outboxId,
                    validatedWorkerId,
                    startedAt
                )
        );
    }

    /**
     * A chamada externa posterior depende de um commit real do STARTED.
     *
     * <p>Por isso uma transação JDBC já aberta pelo chamador não é
     * aceita nesta fronteira.</p>
     */
    private void requireIndependentCommitBoundary() {

        try {

            if (!connection.getAutoCommit()) {

                throw new IllegalStateException(
                    "Publication attempt start requires "
                        + "autoCommit=true so STARTED can be "
                        + "committed before external delivery"
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to verify transaction boundary before "
                    + "starting publication attempt",
                exception
            );
        }
    }

    private PublicationAttemptHandle startInsideTransaction(
        long outboxId,
        String workerId,
        OffsetDateTime startedAt
    ) {

        try {

            LockedOutbox outbox =
                lockOutbox(
                    outboxId
                );

            validateOwnership(
                outbox,
                workerId
            );

            validateStartedAt(
                outbox,
                startedAt
            );

            int attemptNumber =
                nextAttemptNumber(
                    outbox.id()
                );

            long attemptId =
                insertStartedAttempt(
                    outbox,
                    attemptNumber,
                    startedAt
                );

            return new PublicationAttemptHandle(
                attemptId,
                outbox.id(),
                attemptNumber,
                startedAt
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to start publication attempt for outbox item "
                    + outboxId,
                exception
            );
        }
    }

    private LockedOutbox lockOutbox(
        long outboxId
    ) throws SQLException {

        String sql =
            """
            SELECT
                id,
                publication_id,
                channel,
                destination,
                status,
                locked_at,
                locked_by
            FROM publication_outbox
            WHERE id = ?
            FOR UPDATE
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                outboxId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalArgumentException(
                        "Publication outbox item not found: "
                            + outboxId
                    );
                }

                LockedOutbox outbox =
                    new LockedOutbox(
                        resultSet.getLong(
                            "id"
                        ),
                        resultSet.getLong(
                            "publication_id"
                        ),
                        resultSet.getString(
                            "channel"
                        ),
                        resultSet.getString(
                            "destination"
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "locked_at",
                            OffsetDateTime.class
                        ),
                        resultSet.getString(
                            "locked_by"
                        )
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication outbox lookup returned more "
                            + "than one row for id "
                            + outboxId
                    );
                }

                return outbox;
            }
        }
    }

    private void validateOwnership(
        LockedOutbox outbox,
        String workerId
    ) {

        if (!PublicationOutboxStatus.PROCESSING
            .name()
            .equals(
                outbox.status()
            )) {

            throw new IllegalStateException(
                "Publication outbox item "
                    + outbox.id()
                    + " is not PROCESSING"
            );
        }

        if (outbox.lockedAt() == null) {

            throw new IllegalStateException(
                "Publication outbox item "
                    + outbox.id()
                    + " is PROCESSING without lockedAt"
            );
        }

        if (!workerId.equals(
            outbox.lockedBy()
        )) {

            throw new IllegalStateException(
                "Publication outbox item "
                    + outbox.id()
                    + " is not owned by worker "
                    + workerId
            );
        }
    }

    private void validateStartedAt(
        LockedOutbox outbox,
        OffsetDateTime startedAt
    ) {

        if (startedAt.isBefore(
            outbox.lockedAt()
        )) {

            throw new IllegalStateException(
                "Publication attempt cannot start before "
                    + "the outbox lease for item "
                    + outbox.id()
            );
        }
    }

    private int nextAttemptNumber(
        long outboxId
    ) throws SQLException {

        String sql =
            """
            SELECT COALESCE(
                MAX(attempt_number),
                0
            ) AS maximum_attempt_number
            FROM publication_attempt
            WHERE publication_outbox_id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                outboxId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication attempt number query returned no row"
                    );
                }

                int maximum =
                    resultSet.getInt(
                        "maximum_attempt_number"
                    );

                if (maximum == Integer.MAX_VALUE) {

                    throw new IllegalStateException(
                        "Publication attempt number overflow "
                            + "for outbox item "
                            + outboxId
                    );
                }

                return maximum + 1;
            }
        }
    }

    private long insertStartedAttempt(
        LockedOutbox outbox,
        int attemptNumber,
        OffsetDateTime startedAt
    ) throws SQLException {

        String sql =
            """
            INSERT INTO publication_attempt (
                publication_id,
                publication_outbox_id,
                channel,
                target,
                attempt_number,
                status,
                provider_reference,
                error_code,
                started_at,
                finished_at,
                created_at
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                NULL,
                NULL,
                ?,
                NULL,
                ?
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                outbox.publicationId()
            );

            statement.setLong(
                2,
                outbox.id()
            );

            statement.setString(
                3,
                outbox.channel()
            );

            statement.setString(
                4,
                outbox.destination()
            );

            statement.setInt(
                5,
                attemptNumber
            );

            statement.setString(
                6,
                PublicationAttemptStatus.STARTED
                    .name()
            );

            statement.setObject(
                7,
                startedAt
            );

            statement.setObject(
                8,
                startedAt
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication attempt insert returned no row"
                    );
                }

                long attemptId =
                    resultSet.getLong(
                        "id"
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication attempt insert returned more "
                            + "than one row"
                    );
                }

                return attemptId;
            }
        }
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return value;
    }

    private record LockedOutbox(
        long id,
        long publicationId,
        String channel,
        String destination,
        String status,
        OffsetDateTime lockedAt,
        String lockedBy
    ) {
    }
}
