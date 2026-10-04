package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.channel.PublicationResultStatus;
import com.raspingamazon.application.publication.outbox.PublicationAttemptHandle;
import com.raspingamazon.application.publication.outbox.PublicationAttemptStatus;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.port.PublicationAttemptCompletionPort;
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
 * Conclusão PostgreSQL de uma PublicationAttempt previamente
 * persistida como STARTED.
 *
 * <p>Este adapter NÃO cria publication_attempt depois do provider.</p>
 *
 * <p>O protocolo esperado é:</p>
 *
 * <pre>
 * JdbcPublicationAttemptStartAdapter
 *       ↓
 * INSERT STARTED
 *       ↓
 * COMMIT
 *
 * provider
 *       ↓
 *
 * JdbcPublicationAttemptCompletionAdapter
 *       ↓
 * lock outbox
 *       ↓
 * lock exatamente o STARTED recebido
 *       ↓
 * UPDATE da mesma tentativa
 *       ↓
 * atualizar/reagendar outbox
 *       ↓
 * COMMIT
 * </pre>
 *
 * <p>DELIVERY_UNKNOWN é terminal para retry automático. Quando o
 * canal informa ambiguidade externa, a mesma tentativa STARTED e a
 * mesma outbox são concluídas como DELIVERY_UNKNOWN.</p>
 */
public final class JdbcPublicationAttemptCompletionAdapter
    implements PublicationAttemptCompletionPort {

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    private final PublicationOutboxRetryPolicy retryPolicy;

    public JdbcPublicationAttemptCompletionAdapter(
        Connection connection
    ) {

        this(
            connection,
            PublicationOutboxRetryPolicy.noRetry()
        );
    }

    public JdbcPublicationAttemptCompletionAdapter(
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
        PublicationAttemptHandle attempt,
        String workerId,
        PublicationResult result,
        OffsetDateTime completedAt
    ) {

        PublicationAttemptHandle validatedAttempt =
            Objects.requireNonNull(
                attempt,
                "attempt must not be null"
            );

        String validatedWorkerId =
            requireText(
                workerId,
                "workerId"
            );

        PublicationResult validatedResult =
            Objects.requireNonNull(
                result,
                "result must not be null"
            );

        OffsetDateTime validatedCompletedAt =
            Objects.requireNonNull(
                completedAt,
                "completedAt must not be null"
            );

        return transactionAdapter.execute(
            () ->
                completeInsideTransaction(
                    validatedAttempt,
                    validatedWorkerId,
                    validatedResult,
                    validatedCompletedAt
                )
        );
    }

    private PublicationOutboxItem completeInsideTransaction(
        PublicationAttemptHandle handle,
        String workerId,
        PublicationResult result,
        OffsetDateTime completedAt
    ) {

        try {

            LockedOutbox outbox =
                lockOutbox(
                    handle.publicationOutboxId()
                );

            validateOwnership(
                outbox,
                workerId
            );

            LockedAttempt attempt =
                lockAttempt(
                    handle.id()
                );

            validateAttempt(
                handle,
                attempt,
                completedAt
            );

            updateAttempt(
                handle.id(),
                result,
                completedAt
            );

            if (result.status()
                == PublicationResultStatus.FAILED_TRANSIENT) {

                Optional<OffsetDateTime> nextAttemptAt =
                    retryPolicy.nextAttemptAt(
                        handle.attemptNumber(),
                        completedAt
                    );

                if (nextAttemptAt.isPresent()) {

                    OffsetDateTime retryAt =
                        nextAttemptAt.orElseThrow();

                    validateRetryAt(
                        handle.publicationOutboxId(),
                        completedAt,
                        retryAt
                    );

                    return requeueOutbox(
                        handle.publicationOutboxId(),
                        workerId,
                        retryAt,
                        completedAt
                    );
                }
            }

            return finalizeOutbox(
                handle.publicationOutboxId(),
                workerId,
                toOutboxStatus(
                    result.status()
                ),
                completedAt
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to complete publication attempt "
                    + handle.id()
                    + " for outbox item "
                    + handle.publicationOutboxId(),
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

    private LockedAttempt lockAttempt(
        long attemptId
    ) throws SQLException {

        String sql =
            """
            SELECT
                id,
                publication_outbox_id,
                attempt_number,
                status,
                started_at,
                finished_at
            FROM publication_attempt
            WHERE id = ?
            FOR UPDATE
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                attemptId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication attempt not found: "
                            + attemptId
                    );
                }

                LockedAttempt attempt =
                    new LockedAttempt(
                        resultSet.getLong(
                            "id"
                        ),
                        resultSet.getLong(
                            "publication_outbox_id"
                        ),
                        resultSet.getInt(
                            "attempt_number"
                        ),
                        resultSet.getString(
                            "status"
                        ),
                        resultSet.getObject(
                            "started_at",
                            OffsetDateTime.class
                        ),
                        resultSet.getObject(
                            "finished_at",
                            OffsetDateTime.class
                        )
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication attempt lookup returned more "
                            + "than one row for id "
                            + attemptId
                    );
                }

                return attempt;
            }
        }
    }

    private void validateAttempt(
        PublicationAttemptHandle handle,
        LockedAttempt attempt,
        OffsetDateTime completedAt
    ) {

        if (attempt.id()
            != handle.id()) {

            throw new IllegalStateException(
                "Publication attempt identity changed"
            );
        }

        if (attempt.publicationOutboxId()
            != handle.publicationOutboxId()) {

            throw new IllegalStateException(
                "Publication attempt "
                    + handle.id()
                    + " does not belong to outbox item "
                    + handle.publicationOutboxId()
            );
        }

        if (attempt.attemptNumber()
            != handle.attemptNumber()) {

            throw new IllegalStateException(
                "Publication attempt number does not match "
                    + "persisted handle for attempt "
                    + handle.id()
            );
        }

        if (!PublicationAttemptStatus.STARTED
            .name()
            .equals(
                attempt.status()
            )) {

            throw new IllegalStateException(
                "Publication attempt "
                    + handle.id()
                    + " is not STARTED"
            );
        }

        if (attempt.finishedAt() != null) {

            throw new IllegalStateException(
                "STARTED publication attempt "
                    + handle.id()
                    + " unexpectedly contains finishedAt"
            );
        }

        if (attempt.startedAt() == null) {

            throw new IllegalStateException(
                "STARTED publication attempt "
                    + handle.id()
                    + " does not contain startedAt"
            );
        }

        if (!attempt.startedAt()
            .toInstant()
            .equals(
                handle.startedAt()
                    .toInstant()
            )) {

            throw new IllegalStateException(
                "Publication attempt startedAt does not match "
                    + "persisted handle for attempt "
                    + handle.id()
            );
        }

        if (completedAt.isBefore(
            attempt.startedAt()
        )) {

            throw new IllegalStateException(
                "Publication attempt "
                    + handle.id()
                    + " finished before it started"
            );
        }
    }

    private void updateAttempt(
        long attemptId,
        PublicationResult result,
        OffsetDateTime completedAt
    ) throws SQLException {

        String sql =
            """
            UPDATE publication_attempt
            SET
                status = ?,
                provider_reference = ?,
                error_code = ?,
                finished_at = ?
            WHERE id = ?
              AND status = 'STARTED'
              AND finished_at IS NULL
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                result.status()
                    .name()
            );

            statement.setString(
                2,
                result.providerReference()
            );

            statement.setString(
                3,
                result.errorCode()
            );

            statement.setObject(
                4,
                completedAt
            );

            statement.setLong(
                5,
                attemptId
            );

            int updated =
                statement.executeUpdate();

            if (updated != 1) {

                throw new IllegalStateException(
                    "Publication attempt "
                        + attemptId
                        + " could not be completed from STARTED"
                );
            }
        }
    }

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

                PublicationOutboxItem item =
                    readItem(
                        resultSet
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication outbox requeue returned "
                            + "more than one row"
                    );
                }

                return item;
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

                PublicationOutboxItem item =
                    readItem(
                        resultSet
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "Publication outbox completion returned "
                            + "more than one row"
                    );
                }

                return item;
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
        String status,
        OffsetDateTime lockedAt,
        String lockedBy
    ) {
    }

    private record LockedAttempt(
        long id,
        long publicationOutboxId,
        int attemptNumber,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
    ) {
    }
}
