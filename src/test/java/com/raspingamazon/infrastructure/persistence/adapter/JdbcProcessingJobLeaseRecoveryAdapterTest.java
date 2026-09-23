package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryResult;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JdbcProcessingJobLeaseRecoveryAdapterTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-22T23:50:00Z"
        );

    private static final OffsetDateTime EXPIRED_BEFORE =
        NOW.minusMinutes(
            5
        );

    @Test
    void shouldMoveExpiredRunningJobToRetryWait()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        TestData data =
            null;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            long runId =
                insertProcessingRun(
                    connection,
                    "lease-retry"
                );

            long jobId =
                insertRunningJob(
                    connection,
                    runId,
                    "lease-retry-job",
                    2,
                    5,
                    NOW.minusMinutes(
                        10
                    )
                );

            data =
                new TestData(
                    runId,
                    jobId
                );

            JdbcProcessingJobLeaseRecoveryAdapter adapter =
                new JdbcProcessingJobLeaseRecoveryAdapter(
                    connection
                );

            ProcessingJobLeaseRecoveryResult result =
                adapter.recoverExpiredLeases(
                    EXPIRED_BEFORE,
                    NOW,
                    10
                );

            assertEquals(
                1,
                result.retryWaitCount()
            );

            assertEquals(
                0,
                result.deadCount()
            );

            assertEquals(
                1,
                result.totalRecovered()
            );

            PersistedJob job =
                findJob(
                    connection,
                    jobId
                );

            assertEquals(
                "RETRY_WAIT",
                job.status()
            );

            assertEquals(
                2,
                job.attemptCount()
            );

            assertEquals(
                NOW,
                job.availableAt()
            );

            assertNull(
                job.lockedAt()
            );

            assertNull(
                job.lockedBy()
            );

            assertEquals(
                "TRANSIENT",
                job.lastFailureType()
            );

            assertEquals(
                "WORKER_LEASE_EXPIRED",
                job.lastErrorCode()
            );

            assertNull(
                job.finishedAt()
            );

        } finally {

            deleteTestData(
                config,
                data
            );
        }
    }

    @Test
    void shouldMoveExpiredJobToDeadWhenAttemptsAreExhausted()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        TestData data =
            null;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            long runId =
                insertProcessingRun(
                    connection,
                    "lease-dead"
                );

            long jobId =
                insertRunningJob(
                    connection,
                    runId,
                    "lease-dead-job",
                    5,
                    5,
                    NOW.minusMinutes(
                        10
                    )
                );

            data =
                new TestData(
                    runId,
                    jobId
                );

            JdbcProcessingJobLeaseRecoveryAdapter adapter =
                new JdbcProcessingJobLeaseRecoveryAdapter(
                    connection
                );

            ProcessingJobLeaseRecoveryResult result =
                adapter.recoverExpiredLeases(
                    EXPIRED_BEFORE,
                    NOW,
                    10
                );

            assertEquals(
                0,
                result.retryWaitCount()
            );

            assertEquals(
                1,
                result.deadCount()
            );

            PersistedJob job =
                findJob(
                    connection,
                    jobId
                );

            assertEquals(
                "DEAD",
                job.status()
            );

            assertEquals(
                5,
                job.attemptCount()
            );

            assertNull(
                job.lockedAt()
            );

            assertNull(
                job.lockedBy()
            );

            assertEquals(
                "TRANSIENT",
                job.lastFailureType()
            );

            assertEquals(
                "WORKER_LEASE_EXPIRED",
                job.lastErrorCode()
            );

            assertEquals(
                NOW,
                job.finishedAt()
            );

        } finally {

            deleteTestData(
                config,
                data
            );
        }
    }

    @Test
    void shouldNotRecoverRunningJobWithValidLease()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        TestData data =
            null;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            long runId =
                insertProcessingRun(
                    connection,
                    "lease-valid"
                );

            long jobId =
                insertRunningJob(
                    connection,
                    runId,
                    "lease-valid-job",
                    1,
                    5,
                    NOW.minusMinutes(
                        1
                    )
                );

            data =
                new TestData(
                    runId,
                    jobId
                );

            JdbcProcessingJobLeaseRecoveryAdapter adapter =
                new JdbcProcessingJobLeaseRecoveryAdapter(
                    connection
                );

            ProcessingJobLeaseRecoveryResult result =
                adapter.recoverExpiredLeases(
                    EXPIRED_BEFORE,
                    NOW,
                    10
                );

            assertEquals(
                0,
                result.totalRecovered()
            );

            PersistedJob job =
                findJob(
                    connection,
                    jobId
                );

            assertEquals(
                "RUNNING",
                job.status()
            );

            assertEquals(
                "test-worker",
                job.lockedBy()
            );

        } finally {

            deleteTestData(
                config,
                data
            );
        }
    }

    @Test
    void shouldRespectRecoveryBatchSize()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        TestData first =
            null;

        TestData second =
            null;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            long firstRunId =
                insertProcessingRun(
                    connection,
                    "lease-batch-1"
                );

            long firstJobId =
                insertRunningJob(
                    connection,
                    firstRunId,
                    "lease-batch-job-1",
                    1,
                    5,
                    NOW.minusMinutes(
                        20
                    )
                );

            first =
                new TestData(
                    firstRunId,
                    firstJobId
                );

            long secondRunId =
                insertProcessingRun(
                    connection,
                    "lease-batch-2"
                );

            long secondJobId =
                insertRunningJob(
                    connection,
                    secondRunId,
                    "lease-batch-job-2",
                    1,
                    5,
                    NOW.minusMinutes(
                        10
                    )
                );

            second =
                new TestData(
                    secondRunId,
                    secondJobId
                );

            JdbcProcessingJobLeaseRecoveryAdapter adapter =
                new JdbcProcessingJobLeaseRecoveryAdapter(
                    connection
                );

            ProcessingJobLeaseRecoveryResult firstResult =
                adapter.recoverExpiredLeases(
                    EXPIRED_BEFORE,
                    NOW,
                    1
                );

            assertEquals(
                1,
                firstResult.totalRecovered()
            );

            assertEquals(
                "RETRY_WAIT",
                findJob(
                    connection,
                    firstJobId
                ).status()
            );

            assertEquals(
                "RUNNING",
                findJob(
                    connection,
                    secondJobId
                ).status()
            );

            ProcessingJobLeaseRecoveryResult secondResult =
                adapter.recoverExpiredLeases(
                    EXPIRED_BEFORE,
                    NOW,
                    1
                );

            assertEquals(
                1,
                secondResult.totalRecovered()
            );

            assertEquals(
                "RETRY_WAIT",
                findJob(
                    connection,
                    secondJobId
                ).status()
            );

        } finally {

            deleteTestData(
                config,
                second
            );

            deleteTestData(
                config,
                first
            );
        }
    }

    private long insertProcessingRun(
        Connection connection,
        String suffix
    ) throws Exception {

        String sql =
            """
            INSERT INTO processing_run (
                run_key,
                source_uri,
                status,
                requested_at
            )
            VALUES (?, ?, 'PENDING', ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                "test-" + suffix + "-" + System.nanoTime()
            );

            statement.setString(
                2,
                "https://www.amazon.com.br/deals"
            );

            statement.setObject(
                3,
                NOW.minusHours(
                    1
                )
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "ProcessingRun insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertRunningJob(
        Connection connection,
        long processingRunId,
        String idempotencyKey,
        int attemptCount,
        int maxAttempts,
        OffsetDateTime lockedAt
    ) throws Exception {

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
                available_at,
                locked_at,
                locked_by,
                created_at,
                updated_at
            )
            VALUES (
                'COLLECT_DEALS',
                'RUNNING',
                ?,
                NULL,
                NULL,
                ?,
                ?,
                ?,
                ?,
                ?,
                'test-worker',
                ?,
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
                processingRunId
            );

            statement.setString(
                2,
                idempotencyKey
                    + "-"
                    + System.nanoTime()
            );

            statement.setInt(
                3,
                attemptCount
            );

            statement.setInt(
                4,
                maxAttempts
            );

            statement.setObject(
                5,
                NOW.minusHours(
                    1
                )
            );

            statement.setObject(
                6,
                lockedAt
            );

            statement.setObject(
                7,
                NOW.minusHours(
                    1
                )
            );

            statement.setObject(
                8,
                lockedAt
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "ProcessingJob insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private PersistedJob findJob(
        Connection connection,
        long jobId
    ) throws Exception {

        String sql =
            """
            SELECT
                status,
                attempt_count,
                available_at,
                locked_at,
                locked_by,
                last_failure_type,
                last_error_code,
                finished_at
            FROM processing_job
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                jobId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "ProcessingJob not found: "
                            + jobId
                    );
                }

                return new PersistedJob(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getInt(
                        "attempt_count"
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
                    resultSet.getString(
                        "last_failure_type"
                    ),
                    resultSet.getString(
                        "last_error_code"
                    ),
                    resultSet.getObject(
                        "finished_at",
                        OffsetDateTime.class
                    )
                );
            }
        }
    }

    private void deleteTestData(
        ApplicationConfig config,
        TestData data
    ) {

        if (data == null) {
            return;
        }

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM processing_job
                         WHERE id = ?
                         """
                     )) {

                statement.setLong(
                    1,
                    data.jobId()
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM processing_run
                         WHERE id = ?
                         """
                     )) {

                statement.setLong(
                    1,
                    data.processingRunId()
                );

                statement.executeUpdate();
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean lease recovery test data",
                exception
            );
        }
    }

    private record TestData(
        long processingRunId,
        long jobId
    ) {
    }

    private record PersistedJob(
        String status,
        int attemptCount,
        OffsetDateTime availableAt,
        OffsetDateTime lockedAt,
        String lockedBy,
        String lastFailureType,
        String lastErrorCode,
        OffsetDateTime finishedAt
    ) {
    }
}
