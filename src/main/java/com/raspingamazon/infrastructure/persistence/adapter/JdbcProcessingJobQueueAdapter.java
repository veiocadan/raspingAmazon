package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação PostgreSQL da fila durável de processamento.
 *
 * <p>A fila utiliza a própria tabela processing_job como fonte
 * de verdade do estado operacional.</p>
 *
 * <p>A reivindicação utiliza PostgreSQL FOR UPDATE SKIP LOCKED
 * para permitir múltiplos workers concorrentes sem entregar o
 * mesmo job simultaneamente.</p>
 *
 * <p>Este adapter não executa coleta, enriquecimento ou avaliação.
 * Sua responsabilidade é exclusivamente persistir e controlar
 * o ciclo de vida técnico dos jobs.</p>
 */
public final class JdbcProcessingJobQueueAdapter
    implements ProcessingJobQueuePort {

    private final Connection connection;

    public JdbcProcessingJobQueueAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    /**
     * Cria o job de maneira idempotente.
     *
     * <p>A constraint:</p>
     *
     * <pre>
     * UNIQUE (job_type, idempotency_key)
     * </pre>
     *
     * <p>é a autoridade final contra duplicação.</p>
     *
     * <p>Quando já existe um job com a mesma identidade lógica,
     * usamos um UPDATE deliberadamente neutro apenas para que
     * PostgreSQL possa retornar a linha existente na mesma
     * instrução SQL.</p>
     */
    @Override
    public ProcessingJob enqueue(
        ProcessingJobSubmission submission
    ) {

        Objects.requireNonNull(
            submission,
            "submission must not be null"
        );

        String sql =
            """
            INSERT INTO processing_job (
                job_type,
                status,
                processing_run_id,
                deal_candidate_id,
                offer_snapshot_id,
                idempotency_key,
                attempt_count,
                max_attempts,
                available_at
            )
            VALUES (
                ?,
                'PENDING',
                ?,
                ?,
                ?,
                ?,
                0,
                ?,
                ?
            )
            ON CONFLICT (
                job_type,
                idempotency_key
            )
            DO UPDATE
            SET idempotency_key =
                EXCLUDED.idempotency_key
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

            statement.setString(
                1,
                submission.type().name()
            );

            setNullableLong(
                statement,
                2,
                submission.processingRunId()
            );

            setNullableLong(
                statement,
                3,
                submission.dealCandidateId()
            );

            setNullableLong(
                statement,
                4,
                submission.offerSnapshotId()
            );

            statement.setString(
                5,
                submission.idempotencyKey()
            );

            statement.setInt(
                6,
                submission.maxAttempts()
            );

            statement.setObject(
                7,
                submission.availableAt()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "processing job enqueue returned no row"
                    );
                }

                ProcessingJob job =
                    readJob(
                        resultSet
                    );

                validateIdempotentMatch(
                    submission,
                    job
                );

                return job;
            }

        } catch (SQLException exception) {
            throw persistenceFailure(
                "enqueue processing job",
                exception
            );
        }
    }

    /**
     * Reivindica o próximo job disponível.
     *
     * <p>A seleção e a mudança para RUNNING pertencem à mesma
     * instrução SQL. Portanto não existe janela entre:</p>
     *
     * <pre>
     * encontrar job
     *      ↓
     * reservar job
     * </pre>
     *
     * <p>FOR UPDATE SKIP LOCKED faz com que um worker ignore
     * linhas atualmente bloqueadas por outro worker e procure
     * outro trabalho disponível.</p>
     */
    @Override
    public Optional<ProcessingJob> claimNext(
        String workerId,
        OffsetDateTime claimedAt
    ) {

        workerId =
            requireNonBlank(
                workerId,
                "workerId must not be blank"
            );

        Objects.requireNonNull(
            claimedAt,
            "claimedAt must not be null"
        );

        String sql =
            """
            WITH next_job AS (
                SELECT id
                FROM processing_job
                WHERE status IN (
                    'PENDING',
                    'RETRY_WAIT'
                )
                  AND available_at <= ?
                  AND attempt_count < max_attempts
                ORDER BY
                    available_at ASC,
                    id ASC
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE processing_job AS job
            SET
                status = 'RUNNING',
                attempt_count =
                    job.attempt_count + 1,
                locked_at = ?,
                locked_by = ?,
                updated_at = ?
            FROM next_job
            WHERE job.id = next_job.id
            RETURNING
                job.id,
                job.job_type,
                job.status,
                job.processing_run_id,
                job.deal_candidate_id,
                job.offer_snapshot_id,
                job.idempotency_key,
                job.attempt_count,
                job.max_attempts,
                job.available_at,
                job.locked_at,
                job.locked_by,
                job.last_failure_type,
                job.last_error_code,
                job.last_error_message,
                job.created_at,
                job.updated_at,
                job.finished_at
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                claimedAt
            );

            statement.setObject(
                2,
                claimedAt
            );

            statement.setString(
                3,
                workerId
            );

            statement.setObject(
                4,
                claimedAt
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    readJob(
                        resultSet
                    )
                );
            }

        } catch (SQLException exception) {
            throw persistenceFailure(
                "claim processing job",
                exception
            );
        }
    }

    @Override
    public ProcessingJob markSucceeded(
        long jobId,
        String workerId,
        OffsetDateTime finishedAt
    ) {

        requirePositiveId(
            jobId
        );

        workerId =
            requireNonBlank(
                workerId,
                "workerId must not be blank"
            );

        Objects.requireNonNull(
            finishedAt,
            "finishedAt must not be null"
        );

        String sql =
            """
            UPDATE processing_job
            SET
                status = 'SUCCEEDED',
                locked_at = NULL,
                locked_by = NULL,
                updated_at = ?,
                finished_at = ?
            WHERE id = ?
              AND status = 'RUNNING'
              AND locked_by = ?
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
                finishedAt
            );

            statement.setObject(
                2,
                finishedAt
            );

            statement.setLong(
                3,
                jobId
            );

            statement.setString(
                4,
                workerId
            );

            return requireUpdatedJob(
                statement,
                "ProcessingJob cannot be marked SUCCEEDED "
                    + "because it is not RUNNING or is not owned "
                    + "by worker "
                    + workerId
            );

        } catch (SQLException exception) {
            throw persistenceFailure(
                "mark processing job as succeeded",
                exception
            );
        }
    }

    @Override
    public ProcessingJob scheduleRetry(
        long jobId,
        String workerId,
        ProcessingFailure failure,
        OffsetDateTime availableAt,
        OffsetDateTime failedAt
    ) {

        requirePositiveId(
            jobId
        );

        workerId =
            requireNonBlank(
                workerId,
                "workerId must not be blank"
            );

        Objects.requireNonNull(
            failure,
            "failure must not be null"
        );

        Objects.requireNonNull(
            availableAt,
            "availableAt must not be null"
        );

        Objects.requireNonNull(
            failedAt,
            "failedAt must not be null"
        );

        if (failure.type()
            != ProcessingFailureType.TRANSIENT) {

            throw new IllegalArgumentException(
                "Only TRANSIENT failure can be scheduled for retry"
            );
        }

        if (availableAt.isBefore(
            failedAt
        )) {

            throw new IllegalArgumentException(
                "availableAt must not be before failedAt"
            );
        }

        String sql =
            """
            UPDATE processing_job
            SET
                status = 'RETRY_WAIT',
                available_at = ?,
                locked_at = NULL,
                locked_by = NULL,
                last_failure_type = ?,
                last_error_code = ?,
                last_error_message = ?,
                updated_at = ?,
                finished_at = NULL
            WHERE id = ?
              AND status = 'RUNNING'
              AND locked_by = ?
              AND attempt_count < max_attempts
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
                availableAt
            );

            statement.setString(
                2,
                failure.type().name()
            );

            statement.setString(
                3,
                failure.errorCode()
            );

            statement.setString(
                4,
                failure.errorMessage()
            );

            statement.setObject(
                5,
                failedAt
            );

            statement.setLong(
                6,
                jobId
            );

            statement.setString(
                7,
                workerId
            );

            return requireUpdatedJob(
                statement,
                "ProcessingJob cannot be scheduled for retry "
                    + "because it is not RUNNING, is not owned "
                    + "by worker "
                    + workerId
                    + ", or has exhausted maxAttempts"
            );

        } catch (SQLException exception) {
            throw persistenceFailure(
                "schedule processing job retry",
                exception
            );
        }
    }

    @Override
    public ProcessingJob markDead(
        long jobId,
        String workerId,
        ProcessingFailure failure,
        OffsetDateTime failedAt
    ) {

        requirePositiveId(
            jobId
        );

        workerId =
            requireNonBlank(
                workerId,
                "workerId must not be blank"
            );

        Objects.requireNonNull(
            failure,
            "failure must not be null"
        );

        Objects.requireNonNull(
            failedAt,
            "failedAt must not be null"
        );

        String sql =
            """
            UPDATE processing_job
            SET
                status = 'DEAD',
                locked_at = NULL,
                locked_by = NULL,
                last_failure_type = ?,
                last_error_code = ?,
                last_error_message = ?,
                updated_at = ?,
                finished_at = ?
            WHERE id = ?
              AND status = 'RUNNING'
              AND locked_by = ?
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

            statement.setString(
                1,
                failure.type().name()
            );

            statement.setString(
                2,
                failure.errorCode()
            );

            statement.setString(
                3,
                failure.errorMessage()
            );

            statement.setObject(
                4,
                failedAt
            );

            statement.setObject(
                5,
                failedAt
            );

            statement.setLong(
                6,
                jobId
            );

            statement.setString(
                7,
                workerId
            );

            return requireUpdatedJob(
                statement,
                "ProcessingJob cannot be marked DEAD "
                    + "because it is not RUNNING or is not owned "
                    + "by worker "
                    + workerId
            );

        } catch (SQLException exception) {
            throw persistenceFailure(
                "mark processing job as dead",
                exception
            );
        }
    }

    private ProcessingJob requireUpdatedJob(
        PreparedStatement statement,
        String noRowMessage
    ) throws SQLException {

        try (ResultSet resultSet =
                 statement.executeQuery()) {

            if (!resultSet.next()) {
                throw new IllegalStateException(
                    noRowMessage
                );
            }

            return readJob(
                resultSet
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

    private void setNullableLong(
        PreparedStatement statement,
        int index,
        Long value
    ) throws SQLException {

        if (value == null) {

            statement.setObject(
                index,
                null
            );

            return;
        }

        statement.setLong(
            index,
            value
        );
    }

    /**
     * Protege contra reutilização incorreta de uma idempotencyKey.
     *
     * <p>Receber duas vezes a mesma solicitação é permitido.</p>
     *
     * <p>Usar a mesma chave para outro sujeito ou outra política
     * de tentativas representa colisão lógica e deve falhar
     * explicitamente.</p>
     */
    private void validateIdempotentMatch(
        ProcessingJobSubmission submission,
        ProcessingJob persisted
    ) {

        boolean sameLogicalJob =
            persisted.type()
                == submission.type()
            && Objects.equals(
                persisted.processingRunId(),
                submission.processingRunId()
            )
            && Objects.equals(
                persisted.dealCandidateId(),
                submission.dealCandidateId()
            )
            && Objects.equals(
                persisted.offerSnapshotId(),
                submission.offerSnapshotId()
            )
            && persisted.idempotencyKey()
                .equals(
                    submission.idempotencyKey()
                )
            && persisted.maxAttempts()
                == submission.maxAttempts();

        if (!sameLogicalJob) {
            throw new IllegalStateException(
                "ProcessingJob idempotency key collision for "
                    + submission.type()
                    + ":"
                    + submission.idempotencyKey()
            );
        }
    }

    private void requirePositiveId(
        long jobId
    ) {

        if (jobId <= 0) {
            throw new IllegalArgumentException(
                "jobId must be positive"
            );
        }
    }

    private String requireNonBlank(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
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
}
