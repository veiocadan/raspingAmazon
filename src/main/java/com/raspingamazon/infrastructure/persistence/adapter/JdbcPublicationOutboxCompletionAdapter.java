package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.channel.PublicationResultStatus;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxCompletionPort;
import com.raspingamazon.application.publication.outbox.retry.PublicationOutboxRetryPolicy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * ImplementaÃ§Ã£o PostgreSQL da conclusÃ£o de uma tentativa da outbox.
 *
 * <p>A operaÃ§Ã£o inteira pertence a uma Ãºnica transaÃ§Ã£o:</p>
 *
 * <pre>
 * bloquear outbox
 *      â†“
 * validar ownership
 *      â†“
 * recuperar inÃ­cio da tentativa
 *      â†“
 * calcular attemptNumber
 *      â†“
 * inserir publication_attempt
 *      â†“
 * SUCCESS
 *      -> SUCCEEDED
 *
 * FAILED_PERMANENT
 *      -> FAILED_PERMANENT
 *
 * FAILED_TRANSIENT
 *      â†“
 * retry permitido?
 *      â”œâ”€ sim
 *      â”‚    -> PENDING
 *      â”‚    -> novo availableAt
 *      â”‚
 *      â””â”€ nÃ£o
 *           -> FAILED_TRANSIENT terminal
 *      â†“
 * commit
 * </pre>
 *
 * <p>PublicationAttempt representa a evidÃªncia imutÃ¡vel de cada
 * chamada externa.</p>
 *
 * <p>PublicationOutbox representa a unidade durÃ¡vel de trabalho.
 * Um retry reutiliza a mesma linha de outbox; nÃ£o cria nova reserva
 * de quota, nÃ£o recalcula seleÃ§Ã£o e nÃ£o gera nova Publication.</p>
 *
 * <p>O inÃ­cio da tentativa Ã© o instante em que a entrada foi
 * reivindicada pelo worker e recebeu seu lease. Esse instante jÃ¡
 * estÃ¡ persistido em publication_outbox.locked_at.</p>
 *
 * <p>O estado global de Publication nÃ£o Ã© utilizado como fonte de
 * verdade da entrega por canal/destino.</p>
 */
public final class JdbcPublicationOutboxCompletionAdapter
    implements PublicationOutboxCompletionPort {

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    private final PublicationOutboxRetryPolicy retryPolicy;

    /**
     * Construtor de compatibilidade.
     *
     * <p>Preserva o comportamento histÃ³rico de uma Ãºnica tentativa.
     * A composiÃ§Ã£o operacional da FASE 19 deverÃ¡ utilizar o
     * construtor que recebe PublicationOutboxRetryPolicy.</p>
     */
    public JdbcPublicationOutboxCompletionAdapter(
        Connection connection
    ) {

        this(
            connection,
            PublicationOutboxRetryPolicy.noRetry()
        );
    }

    /**
     * Construtor operacional com retry explÃ­cito.
     */
    public JdbcPublicationOutboxCompletionAdapter(
        Connection connection,
        PublicationOutboxRetryPolicy retryPolicy
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        this.retryPolicy =
            Objects.requireNonNull(
                retryPolicy,
                "retryPolicy must not be null"
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

            OffsetDateTime startedAt =
                resolveAttemptStartedAt(
                    outbox,
                    completedAt
                );

            int attemptNumber =
                nextAttemptNumber(
                    outboxId
                );

            /*
             * A tentativa Ã© registrada antes da decisÃ£o de retry.
             *
             * Como tudo ocorre na mesma transaÃ§Ã£o, nÃ£o existe estado
             * persistente em que a outbox foi reagendada sem a
             * respectiva evidÃªncia de tentativa.
             */
            insertAttempt(
                outbox,
                attemptNumber,
                result,
                startedAt,
                completedAt
            );

            if (result.status()
                == PublicationResultStatus.FAILED_TRANSIENT) {

                Optional<OffsetDateTime> nextAttemptAt =
                    retryPolicy.nextAttemptAt(
                        attemptNumber,
                        completedAt
                    );

                if (nextAttemptAt.isPresent()) {

                    OffsetDateTime retryAt =
                        nextAttemptAt.orElseThrow();

                    validateRetryAt(
                        outboxId,
                        completedAt,
                        retryAt
                    );

                    return requeueOutbox(
                        outboxId,
                        workerId,
                        retryAt,
                        completedAt
                    );
                }
            }

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
                    resultSet.getObject(
                        "locked_at",
                        OffsetDateTime.class
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

    private OffsetDateTime resolveAttemptStartedAt(
        LockedOutbox outbox,
        OffsetDateTime completedAt
    ) {

        OffsetDateTime startedAt =
            outbox.lockedAt();

        if (startedAt == null) {

            throw new IllegalStateException(
                "Publication outbox item "
                    + outbox.id()
                    + " is PROCESSING without lockedAt"
            );
        }

        if (completedAt.isBefore(
            startedAt
        )) {

            throw new IllegalStateException(
                "Publication attempt finished before it started "
                    + "for outbox item "
                    + outbox.id()
            );
        }

        return startedAt;
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
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
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
                startedAt
            );

            statement.setObject(
                10,
                finishedAt
            );

            /*
             * Mantemos created_at com sua semÃ¢ntica histÃ³rica atual:
             * o instante em que a tentativa foi materializada como
             * registro persistente.
             *
             * started_at e finished_at sÃ£o a autoridade para duraÃ§Ã£o.
             */
            statement.setObject(
                11,
                finishedAt
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

    /**
     * Reagenda a MESMA unidade de outbox.
     *
     * <p>NÃ£o cria nova Publication, nova SelectionRun, nova reserva
     * de quota ou nova linha de outbox.</p>
     */
    private PublicationOutboxItem requeueOutbox(
        long outboxId,
        String workerId,
        OffsetDateTime nextAvailableAt,
        OffsetDateTime completedAt
    ) throws SQLException {

        String sql =
            """
            UPDATE publication_outbox
            SET
                status = 'PENDING',
                available_at = ?,
                locked_at = NULL,
                locked_by = NULL,
                updated_at = ?,
                finished_at = NULL
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

            statement.setObject(
                1,
                nextAvailableAt
            );

            statement.setObject(
                2,
                completedAt
            );

            statement.setLong(
                3,
                outboxId
            );

            statement.setString(
                4,
                workerId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication outbox item "
                            + outboxId
                            + " could not be requeued by worker "
                            + workerId
                    );
                }

                return readItem(
                    resultSet
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

    private void validateRetryAt(
        long outboxId,
        OffsetDateTime completedAt,
        OffsetDateTime nextAttemptAt
    ) {

        Objects.requireNonNull(
            nextAttemptAt,
            "nextAttemptAt must not be null"
        );

        if (!nextAttemptAt.isAfter(
            completedAt
        )) {

            throw new IllegalStateException(
                "Retry policy returned non-future nextAttemptAt "
                    + "for publication outbox item "
                    + outboxId
            );
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

            case DELIVERY_UNKNOWN ->
                PublicationOutboxStatus.DELIVERY_UNKNOWN;
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
        OffsetDateTime lockedAt,
        String lockedBy
    ) {
    }
}
