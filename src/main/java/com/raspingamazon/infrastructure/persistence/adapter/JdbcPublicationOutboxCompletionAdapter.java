package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.channel.PublicationResultStatus;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxCompletionPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Implementação PostgreSQL da conclusão de uma entrada da outbox.
 *
 * <p>A operação inteira pertence a uma única transação:</p>
 *
 * <pre>
 * bloquear outbox
 *      ↓
 * validar ownership
 *      ↓
 * calcular attemptNumber
 *      ↓
 * inserir publication_attempt
 *      ↓
 * finalizar publication_outbox
 *      ↓
 * commit
 * </pre>
 *
 * <p>PublicationAttempt representa a evidência da execução externa.
 * PublicationOutbox representa a unidade durável de trabalho.</p>
 *
 * <p>O estado global de Publication não é utilizado como fonte de
 * verdade da entrega por canal/destino.</p>
 */
public final class JdbcPublicationOutboxCompletionAdapter
    implements PublicationOutboxCompletionPort {

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    public JdbcPublicationOutboxCompletionAdapter(
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
    public PublicationOutboxItem complete(
        long outboxId,
        String workerId,
        PublicationResult result,
        OffsetDateTime completedAt
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
            result,
            "result must not be null"
        );

        Objects.requireNonNull(
            completedAt,
            "completedAt must not be null"
        );

        return transactionAdapter.execute(
            () ->
                completeInsideTransaction(
                    outboxId,
                    validatedWorkerId,
                    result,
                    completedAt
                )
        );
    }

    private PublicationOutboxItem completeInsideTransaction(
        long outboxId,
        String workerId,
        PublicationResult result,
        OffsetDateTime completedAt
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

            int attemptNumber =
                nextAttemptNumber(
                    outboxId
                );

            insertAttempt(
                outbox,
                attemptNumber,
                result,
                completedAt
            );

            return finalizeOutbox(
                outboxId,
                workerId,
                toOutboxStatus(
                    result.status()
                ),
                completedAt
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to complete publication outbox item "
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

                return new LockedOutbox(
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
                    resultSet.getString(
                        "locked_by"
                    )
                );
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
                        "Publication attempt number overflow"
                    );
                }

                return maximum + 1;
            }
        }
    }

    private void insertAttempt(
        LockedOutbox outbox,
        int attemptNumber,
        PublicationResult result,
        OffsetDateTime completedAt
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
                created_at
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?
            )
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
                result.status()
                    .name()
            );

            statement.setString(
                7,
                result.providerReference()
            );

            statement.setString(
                8,
                result.errorCode()
            );

            statement.setObject(
                9,
                completedAt
            );

            int inserted =
                statement.executeUpdate();

            if (inserted != 1) {

                throw new IllegalStateException(
                    "Publication attempt insert affected "
                        + inserted
                        + " rows"
                );
            }
        }
    }

    private PublicationOutboxItem finalizeOutbox(
        long outboxId,
        String workerId,
        PublicationOutboxStatus terminalStatus,
        OffsetDateTime completedAt
    ) throws SQLException {

        String sql =
            """
            UPDATE publication_outbox
            SET
                status = ?,
                locked_at = NULL,
                locked_by = NULL,
                updated_at = ?,
                finished_at = ?
            WHERE id = ?
              AND status = 'PROCESSING'
              AND locked_by = ?
            RETURNING
                id,
                publication_id,
                selection_run_id,
                selection_position,
                channel,
                destination,
                content,
                quota_profile_version,
                quota_date,
                status,
                available_at,
                locked_at,
                locked_by,
                created_at,
                updated_at,
                finished_at
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                terminalStatus.name()
            );

            statement.setObject(
                2,
                completedAt
            );

            statement.setObject(
                3,
                completedAt
            );

            statement.setLong(
                4,
                outboxId
            );

            statement.setString(
                5,
                workerId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication outbox item "
                            + outboxId
                            + " could not be finalized by worker "
                            + workerId
                    );
                }

                return readItem(
                    resultSet
                );
            }
        }
    }

    private PublicationOutboxStatus toOutboxStatus(
        PublicationResultStatus resultStatus
    ) {

        return switch (resultStatus) {

            case SUCCESS ->
                PublicationOutboxStatus.SUCCEEDED;

            case FAILED_TRANSIENT ->
                PublicationOutboxStatus.FAILED_TRANSIENT;

            case FAILED_PERMANENT ->
                PublicationOutboxStatus.FAILED_PERMANENT;
        };
    }

    private PublicationOutboxItem readItem(
        ResultSet resultSet
    ) throws SQLException {

        return new PublicationOutboxItem(
            resultSet.getLong(
                "id"
            ),
            resultSet.getLong(
                "publication_id"
            ),
            resultSet.getLong(
                "selection_run_id"
            ),
            resultSet.getInt(
                "selection_position"
            ),
            resultSet.getString(
                "channel"
            ),
            resultSet.getString(
                "destination"
            ),
            resultSet.getString(
                "content"
            ),
            resultSet.getString(
                "quota_profile_version"
            ),
            resultSet.getObject(
                "quota_date",
                LocalDate.class
            ),
            PublicationOutboxStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            resultSet.getObject(
                "available_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "locked_at",
                OffsetDateTime.class
            ),
            resultSet.getString(
                "locked_by"
            ),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "updated_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "finished_at",
                OffsetDateTime.class
            )
        );
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
        String lockedBy
    ) {
    }
}
