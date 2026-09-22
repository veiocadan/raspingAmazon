package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryPort;
import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Recuperação PostgreSQL de jobs abandonados em RUNNING.
 *
 * <p>A seleção utiliza FOR UPDATE SKIP LOCKED para permitir que
 * múltiplos processos façam recuperação simultaneamente sem escolher
 * o mesmo lote.</p>
 */
public final class JdbcProcessingJobLeaseRecoveryAdapter
    implements ProcessingJobLeaseRecoveryPort {

    private static final String FAILURE_TYPE =
        "TRANSIENT";

    private static final String ERROR_CODE =
        "WORKER_LEASE_EXPIRED";

    private static final String ERROR_MESSAGE =
        "Worker lease expired before ProcessingJob completion";

    private final Connection connection;

    public JdbcProcessingJobLeaseRecoveryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public ProcessingJobLeaseRecoveryResult recoverExpiredLeases(
        OffsetDateTime leaseExpiredBefore,
        OffsetDateTime recoveredAt,
        int batchSize
    ) {

        Objects.requireNonNull(
            leaseExpiredBefore,
            "leaseExpiredBefore must not be null"
        );

        Objects.requireNonNull(
            recoveredAt,
            "recoveredAt must not be null"
        );

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                "batchSize must be positive"
            );
        }

        if (recoveredAt.isBefore(
            leaseExpiredBefore
        )) {

            throw new IllegalArgumentException(
                "recoveredAt must not be before leaseExpiredBefore"
            );
        }

        String sql =
            """
            WITH expired_jobs AS (
                SELECT id
                FROM processing_job
                WHERE status = 'RUNNING'
                  AND locked_at IS NOT NULL
                  AND locked_at <= ?
                ORDER BY
                    locked_at ASC,
                    id ASC
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE processing_job AS job
            SET
                status =
                    CASE
                        WHEN job.attempt_count < job.max_attempts
                            THEN 'RETRY_WAIT'
                        ELSE 'DEAD'
                    END,
                available_at =
                    CASE
                        WHEN job.attempt_count < job.max_attempts
                            THEN ?
                        ELSE job.available_at
                    END,
                locked_at = NULL,
                locked_by = NULL,
                last_failure_type = ?,
                last_error_code = ?,
                last_error_message = ?,
                updated_at = ?,
                finished_at =
                    CASE
                        WHEN job.attempt_count < job.max_attempts
                            THEN NULL
                        ELSE ?
                    END
            FROM expired_jobs
            WHERE job.id = expired_jobs.id
            RETURNING job.status
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                leaseExpiredBefore
            );

            statement.setInt(
                2,
                batchSize
            );

            statement.setObject(
                3,
                recoveredAt
            );

            statement.setString(
                4,
                FAILURE_TYPE
            );

            statement.setString(
                5,
                ERROR_CODE
            );

            statement.setString(
                6,
                ERROR_MESSAGE
            );

            statement.setObject(
                7,
                recoveredAt
            );

            statement.setObject(
                8,
                recoveredAt
            );

            int retryWaitCount =
                0;

            int deadCount =
                0;

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    String status =
                        resultSet.getString(
                            "status"
                        );

                    switch (status) {

                        case "RETRY_WAIT" ->
                            retryWaitCount++;

                        case "DEAD" ->
                            deadCount++;

                        default ->
                            throw new IllegalStateException(
                                "Unexpected recovered ProcessingJob status: "
                                    + status
                            );
                    }
                }
            }

            return new ProcessingJobLeaseRecoveryResult(
                retryWaitCount,
                deadCount
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Could not recover expired ProcessingJob leases",
                exception
            );
        }
    }
}
