package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.reprocess.ProcessingJobReprocessRequest;
import com.raspingamazon.application.orchestration.reprocess.ProcessingJobReprocessResult;
import com.raspingamazon.application.orchestration.reprocess.port.ProcessingJobReprocessPort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação PostgreSQL do reprocessamento operacional de
 * ProcessingJob em dead-letter lógico.
 *
 * <p>O próprio {@code processing_job} em status {@code DEAD} é a
 * autoridade do dead-letter. O reprocessamento:</p>
 *
 * <ol>
 *     <li>bloqueia a linha do job;</li>
 *     <li>resolve replay idempotente por requestKey;</li>
 *     <li>registra a decisão operacional em
 *         processing_job_reprocess_event;</li>
 *     <li>reabre a mesma identidade lógica como PENDING.</li>
 * </ol>
 *
 * <p>Nenhum novo ProcessingJob é criado. A constraint histórica
 * {@code UNIQUE(job_type, idempotency_key)} permanece intacta.</p>
 *
 * <p>O INSERT do evento utiliza ON CONFLICT DO NOTHING. Isso também
 * fecha a corrida em que a mesma requestKey é apresentada
 * simultaneamente para jobs diferentes: uma transação vence a
 * constraint e a outra detecta explicitamente a colisão.</p>
 */
public final class JdbcProcessingJobReprocessAdapter
    implements ProcessingJobReprocessPort {

    private final Connection connection;

    private final JdbcTransactionAdapter transaction;

    public JdbcProcessingJobReprocessAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        this.transaction =
            new JdbcTransactionAdapter(
                connection
            );
    }

    @Override
    public ProcessingJobReprocessResult reprocess(
        ProcessingJobReprocessRequest request,
        OffsetDateTime requestedAt
    ) {

        ProcessingJobReprocessRequest validatedRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        OffsetDateTime validatedRequestedAt =
            Objects.requireNonNull(
                requestedAt,
                "requestedAt must not be null"
            );

        return transaction.execute(
            () ->
                reprocessInsideTransaction(
                    validatedRequest,
                    validatedRequestedAt
                )
        );
    }

    private ProcessingJobReprocessResult reprocessInsideTransaction(
        ProcessingJobReprocessRequest request,
        OffsetDateTime requestedAt
    ) {

        ProcessingJob lockedJob =
            lockJob(
                request.processingJobId()
            );

        Optional<ReprocessEvent> existingEvent =
            findEventByRequestKey(
                request.requestKey()
            );

        if (existingEvent.isPresent()) {

            return replayExistingEvent(
                request,
                lockedJob,
                existingEvent.orElseThrow()
            );
        }

        requireDead(
            lockedJob
        );

        Optional<Long> insertedEventId =
            insertReprocessEvent(
                request,
                requestedAt,
                lockedJob
            );

        if (insertedEventId.isEmpty()) {

            /*
             * Outra transação venceu a UNIQUE(request_key).
             *
             * O INSERT concorrente já terminou para que PostgreSQL
             * pudesse decidir o conflito. Lemos então a autoridade
             * persistida e tratamos replay ou colisão.
             */
            ReprocessEvent concurrentEvent =
                findEventByRequestKey(
                    request.requestKey()
                ).orElseThrow(
                    () ->
                        new IllegalStateException(
                            "ProcessingJob reprocess requestKey conflict "
                                + "was detected but no persisted event "
                                + "could be read"
                        )
                );

            return replayExistingEvent(
                request,
                lockedJob,
                concurrentEvent
            );
        }

        ProcessingJob reopened =
            reopenDeadJob(
                lockedJob.id(),
                requestedAt
            );

        return new ProcessingJobReprocessResult(
            insertedEventId.orElseThrow(),
            request.requestKey(),
            reopened,
            true
        );
    }

    private ProcessingJobReprocessResult replayExistingEvent(
        ProcessingJobReprocessRequest request,
        ProcessingJob currentJob,
        ReprocessEvent event
    ) {

        validateEventMatch(
            request,
            event
        );

        return new ProcessingJobReprocessResult(
            event.id(),
            event.requestKey(),
            currentJob,
            false
        );
    }

    private ProcessingJob lockJob(
        long processingJobId
    ) {

        String sql =
            """
            SELECT
                id,
                job_type,
                status,
                processing_run_id,
                deal_candidate_id,
                offer_snapshot_id,
                idempotency_key,
                attempt_count,
                max_attempts,
                available_at,
                locked_at,
                locked_by,
                last_failure_type,
                last_error_code,
                last_error_message,
                created_at,
                updated_at,
                finished_at
            FROM processing_job
            WHERE id = ?
            FOR UPDATE
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                processingJobId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "ProcessingJob "
                            + processingJobId
                            + " does not exist"
                    );
                }

                return readJob(
                    resultSet
                );
            }

        } catch (SQLException exception) {

            throw persistenceFailure(
                "lock ProcessingJob for reprocessing",
                exception
            );
        }
    }

    private Optional<ReprocessEvent> findEventByRequestKey(
        String requestKey
    ) {

        String sql =
            """
            SELECT
                id,
                processing_job_id,
                request_key,
                requested_by,
                reason,
                requested_at
            FROM processing_job_reprocess_event
            WHERE request_key = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                requestKey
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    new ReprocessEvent(
                        resultSet.getLong(
                            "id"
                        ),
                        resultSet.getLong(
                            "processing_job_id"
                        ),
                        resultSet.getString(
                            "request_key"
                        ),
                        resultSet.getString(
                            "requested_by"
                        ),
                        resultSet.getString(
                            "reason"
                        ),
                        resultSet.getObject(
                            "requested_at",
                            OffsetDateTime.class
                        )
                    )
                );
            }

        } catch (SQLException exception) {

            throw persistenceFailure(
                "read ProcessingJob reprocess event",
                exception
            );
        }
    }

    private Optional<Long> insertReprocessEvent(
        ProcessingJobReprocessRequest request,
        OffsetDateTime requestedAt,
        ProcessingJob deadJob
    ) {

        String sql =
            """
            INSERT INTO processing_job_reprocess_event (
                processing_job_id,
                request_key,
                requested_by,
                reason,
                requested_at,
                previous_status,
                previous_attempt_count,
                previous_max_attempts,
                previous_failure_type,
                previous_error_code,
                previous_error_message,
                previous_finished_at
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
                ?,
                ?
            )
            ON CONFLICT (
                request_key
            )
            DO NOTHING
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                deadJob.id()
            );

            statement.setString(
                2,
                request.requestKey()
            );

            statement.setString(
                3,
                request.requestedBy()
            );

            statement.setString(
                4,
                request.reason()
            );

            statement.setObject(
                5,
                requestedAt
            );

            statement.setString(
                6,
                deadJob.status()
                    .name()
            );

            statement.setInt(
                7,
                deadJob.attemptCount()
            );

            statement.setInt(
                8,
                deadJob.maxAttempts()
            );

            if (deadJob.lastFailureType()
                == null) {

                statement.setObject(
                    9,
                    null
                );

            } else {

                statement.setString(
                    9,
                    deadJob.lastFailureType()
                        .name()
                );
            }

            statement.setString(
                10,
                deadJob.lastErrorCode()
            );

            statement.setString(
                11,
                deadJob.lastErrorMessage()
            );

            statement.setObject(
                12,
                deadJob.finishedAt()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    resultSet.getLong(
                        "id"
                    )
                );
            }

        } catch (SQLException exception) {

            throw persistenceFailure(
                "insert ProcessingJob reprocess event",
                exception
            );
        }
    }

    private ProcessingJob reopenDeadJob(
        long processingJobId,
        OffsetDateTime requestedAt
    ) {

        String sql =
            """
            UPDATE processing_job
            SET
                status = 'PENDING',
                attempt_count = 0,
                available_at = ?,
                locked_at = NULL,
                locked_by = NULL,
                last_failure_type = NULL,
                last_error_code = NULL,
                last_error_message = NULL,
                updated_at = ?,
                finished_at = NULL
            WHERE id = ?
              AND status = 'DEAD'
            RETURNING
                id,
                job_type,
                status,
                processing_run_id,
                deal_candidate_id,
                offer_snapshot_id,
                idempotency_key,
                attempt_count,
                max_attempts,
                available_at,
                locked_at,
                locked_by,
                last_failure_type,
                last_error_code,
                last_error_message,
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
                requestedAt
            );

            statement.setObject(
                2,
                requestedAt
            );

            statement.setLong(
                3,
                processingJobId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "ProcessingJob "
                            + processingJobId
                            + " is no longer DEAD"
                    );
                }

                return readJob(
                    resultSet
                );
            }

        } catch (SQLException exception) {

            throw persistenceFailure(
                "reopen DEAD ProcessingJob",
                exception
            );
        }
    }

    private void requireDead(
        ProcessingJob job
    ) {

        if (job.status()
            != ProcessingJobStatus.DEAD) {

            throw new IllegalStateException(
                "ProcessingJob "
                    + job.id()
                    + " cannot be reprocessed because status is "
                    + job.status()
            );
        }
    }

    private void validateEventMatch(
        ProcessingJobReprocessRequest request,
        ReprocessEvent event
    ) {

        boolean sameDecision =
            event.processingJobId()
                == request.processingJobId()
            && event.requestKey()
                .equals(
                    request.requestKey()
                )
            && event.requestedBy()
                .equals(
                    request.requestedBy()
                )
            && event.reason()
                .equals(
                    request.reason()
                );

        if (!sameDecision) {

            throw new IllegalStateException(
                "ProcessingJob reprocess requestKey collision: "
                    + request.requestKey()
            );
        }
    }

    private ProcessingJob readJob(
        ResultSet resultSet
    ) throws SQLException {

        return new ProcessingJob(
            resultSet.getLong(
                "id"
            ),
            ProcessingJobType.valueOf(
                resultSet.getString(
                    "job_type"
                )
            ),
            ProcessingJobStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            getNullableLong(
                resultSet,
                "processing_run_id"
            ),
            getNullableLong(
                resultSet,
                "deal_candidate_id"
            ),
            getNullableLong(
                resultSet,
                "offer_snapshot_id"
            ),
            resultSet.getString(
                "idempotency_key"
            ),
            resultSet.getInt(
                "attempt_count"
            ),
            resultSet.getInt(
                "max_attempts"
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
            getNullableFailureType(
                resultSet
            ),
            resultSet.getString(
                "last_error_code"
            ),
            resultSet.getString(
                "last_error_message"
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

    private ProcessingFailureType getNullableFailureType(
        ResultSet resultSet
    ) throws SQLException {

        String value =
            resultSet.getString(
                "last_failure_type"
            );

        if (value == null) {
            return null;
        }

        return ProcessingFailureType.valueOf(
            value
        );
    }

    private Long getNullableLong(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        long value =
            resultSet.getLong(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private IllegalStateException persistenceFailure(
        String operation,
        SQLException exception
    ) {

        return new IllegalStateException(
            "Could not "
                + operation,
            exception
        );
    }

    private record ReprocessEvent(
        long id,
        long processingJobId,
        String requestKey,
        String requestedBy,
        String reason,
        OffsetDateTime requestedAt
    ) {
    }
}
