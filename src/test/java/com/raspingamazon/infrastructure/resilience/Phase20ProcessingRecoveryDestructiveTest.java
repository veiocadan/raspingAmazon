package com.raspingamazon.infrastructure.resilience;

import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryResult;
import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryService;
import com.raspingamazon.application.orchestration.reprocess.ProcessingJobReprocessRequest;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingJobLeaseRecoveryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingJobReprocessAdapter;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@PostgresIntegrationTest
class Phase20ProcessingRecoveryDestructiveTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2000-01-01T00:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW.toInstant(),
            ZoneOffset.UTC
        );

    @Test
    void shouldDrainExpiredProcessingJobsBeyondSingleRecoveryPage()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                long firstRun =
                    insertProcessingRun(
                        connection,
                        "retry-a"
                    );

                long secondRun =
                    insertProcessingRun(
                        connection,
                        "dead"
                    );

                long thirdRun =
                    insertProcessingRun(
                        connection,
                        "retry-b"
                    );

                long firstJob =
                    insertRunningCollectJob(
                        connection,
                        firstRun,
                        1,
                        3,
                        "retry-a"
                    );

                long deadJob =
                    insertRunningCollectJob(
                        connection,
                        secondRun,
                        3,
                        3,
                        "dead"
                    );

                long thirdJob =
                    insertRunningCollectJob(
                        connection,
                        thirdRun,
                        2,
                        3,
                        "retry-b"
                    );

                ProcessingJobLeaseRecoveryResult result =
                    new ProcessingJobLeaseRecoveryService(
                        new JdbcProcessingJobLeaseRecoveryAdapter(
                            connection
                        ),
                        Duration.ofMinutes(
                            15
                        ),
                        1,
                        CLOCK
                    ).recoverUntilQuiescent();

                assertEquals(
                    2,
                    result.retryWaitCount()
                );

                assertEquals(
                    1,
                    result.deadCount()
                );

                assertRecoveredJob(
                    connection,
                    firstJob,
                    "RETRY_WAIT",
                    null
                );

                assertRecoveredJob(
                    connection,
                    deadJob,
                    "DEAD",
                    NOW
                );

                assertRecoveredJob(
                    connection,
                    thirdJob,
                    "RETRY_WAIT",
                    null
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRecoverExhaustedJobToDeadThenReprocessSamePersistedIdentity()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                long processingRunId =
                    insertProcessingRun(
                        connection,
                        "controlled-reprocess"
                    );

                String idempotencyKey =
                    uniqueToken(
                        "collect-reprocess"
                    );

                long jobId =
                    insertRunningCollectJob(
                        connection,
                        processingRunId,
                        3,
                        3,
                        idempotencyKey
                    );

                ProcessingJobLeaseRecoveryResult recovery =
                    new ProcessingJobLeaseRecoveryService(
                        new JdbcProcessingJobLeaseRecoveryAdapter(
                            connection
                        ),
                        Duration.ofMinutes(
                            15
                        ),
                        1,
                        CLOCK
                    ).recoverUntilQuiescent();

                assertEquals(
                    1,
                    recovery.deadCount()
                );

                assertRecoveredJob(
                    connection,
                    jobId,
                    "DEAD",
                    NOW
                );

                OffsetDateTime requestedAt =
                    NOW.plusMinutes(
                        1
                    );

                new JdbcProcessingJobReprocessAdapter(
                    connection
                ).reprocess(
                    new ProcessingJobReprocessRequest(
                        jobId,
                        uniqueToken(
                            "operator-request"
                        ),
                        "phase20-destructive-test",
                        "controlled recovery after simulated worker death"
                    ),
                    requestedAt
                );

                PersistedJob reopened =
                    loadJob(
                        connection,
                        jobId
                    );

                assertEquals(
                    jobId,
                    reopened.id()
                );

                assertEquals(
                    "PENDING",
                    reopened.status()
                );

                assertEquals(
                    0,
                    reopened.attemptCount()
                );

                assertEquals(
                    3,
                    reopened.maxAttempts()
                );

                assertEquals(
                    requestedAt.toInstant(),
                    reopened.availableAt()
                        .toInstant()
                );

                assertNull(
                    reopened.lockedAt()
                );

                assertNull(
                    reopened.lockedBy()
                );

                assertNull(
                    reopened.lastFailureType()
                );

                assertNull(
                    reopened.lastErrorCode()
                );

                assertNull(
                    reopened.finishedAt()
                );

                assertEquals(
                    1L,
                    countJobsByLogicalIdentity(
                        connection,
                        idempotencyKey
                    )
                );

            } finally {

                connection.rollback();
            }
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
                requested_at,
                started_at,
                completed_at,
                created_at,
                updated_at
            )
            VALUES (
                ?,
                'https://example.invalid/phase20',
                'RUNNING',
                ?,
                ?,
                NULL,
                ?,
                ?
            )
            RETURNING id
            """;

        OffsetDateTime createdAt =
            NOW.minusHours(
                2
            );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                uniqueToken(
                    "run-" + suffix
                )
            );

            statement.setObject(
                2,
                createdAt
            );

            statement.setObject(
                3,
                createdAt
            );

            statement.setObject(
                4,
                createdAt
            );

            statement.setObject(
                5,
                createdAt
            );

            return returnedId(
                statement
            );
        }
    }

    private long insertRunningCollectJob(
        Connection connection,
        long processingRunId,
        int attemptCount,
        int maxAttempts,
        String keySuffix
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
                last_failure_type,
                last_error_code,
                last_error_message,
                created_at,
                updated_at,
                finished_at
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
                'simulated-dead-worker',
                NULL,
                NULL,
                NULL,
                ?,
                ?,
                NULL
            )
            RETURNING id
            """;

        OffsetDateTime lockedAt =
            NOW.minusHours(
                1
            );

        OffsetDateTime createdAt =
            NOW.minusHours(
                2
            );

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
                keySuffix.startsWith(
                    "collect-"
                )
                    ? keySuffix
                    : uniqueToken(
                        "collect-" + keySuffix
                    )
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
                createdAt
            );

            statement.setObject(
                6,
                lockedAt
            );

            statement.setObject(
                7,
                createdAt
            );

            statement.setObject(
                8,
                lockedAt
            );

            return returnedId(
                statement
            );
        }
    }

    private void assertRecoveredJob(
        Connection connection,
        long jobId,
        String expectedStatus,
        OffsetDateTime expectedFinishedAt
    ) throws Exception {

        PersistedJob job =
            loadJob(
                connection,
                jobId
            );

        assertEquals(
            expectedStatus,
            job.status()
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

        if (expectedFinishedAt == null) {

            assertNull(
                job.finishedAt()
            );

        } else {

            assertEquals(
                expectedFinishedAt.toInstant(),
                job.finishedAt()
                    .toInstant()
            );
        }
    }

    private PersistedJob loadJob(
        Connection connection,
        long jobId
    ) throws Exception {

        String sql =
            """
            SELECT
                id,
                status,
                attempt_count,
                max_attempts,
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
                    resultSet.getLong(
                        "id"
                    ),
                    resultSet.getString(
                        "status"
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

    private long countJobsByLogicalIdentity(
        Connection connection,
        String idempotencyKey
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM processing_job
            WHERE job_type = 'COLLECT_DEALS'
              AND idempotency_key = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                idempotencyKey
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    private long returnedId(
        PreparedStatement statement
    ) throws Exception {

        try (ResultSet resultSet =
                 statement.executeQuery()) {

            if (!resultSet.next()) {

                throw new IllegalStateException(
                    "INSERT returned no id"
                );
            }

            return resultSet.getLong(
                "id"
            );
        }
    }

    private String uniqueToken(
        String prefix
    ) {

        return prefix
            + ":"
            + UUID.randomUUID();
    }

    private record PersistedJob(
        long id,
        String status,
        int attemptCount,
        int maxAttempts,
        OffsetDateTime availableAt,
        OffsetDateTime lockedAt,
        String lockedBy,
        String lastFailureType,
        String lastErrorCode,
        OffsetDateTime finishedAt
    ) {
    }
}
