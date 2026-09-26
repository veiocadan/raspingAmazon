package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcProcessingJobQueueAdapterTest {

    private static final OffsetDateTime BASE_TIME =
        OffsetDateTime.parse(
            "2026-09-22T18:00:00-03:00"
        );

    @Test
    void shouldEnqueueIdempotently()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long runId =
            0L;

        long jobId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            runId =
                insertProcessingRun(
                    connection
                );

            JdbcProcessingJobQueueAdapter adapter =
                new JdbcProcessingJobQueueAdapter(
                    connection
                );

            ProcessingJobSubmission submission =
                ProcessingJobSubmission.collectDeals(
                    runId,
                    "collect:" + runId,
                    5,
                    BASE_TIME
                );

            ProcessingJob first =
                adapter.enqueue(
                    submission
                );

            ProcessingJob second =
                adapter.enqueue(
                    submission
                );

            jobId =
                first.id();

            assertEquals(
                first.id(),
                second.id()
            );

            assertEquals(
                ProcessingJobStatus.PENDING,
                first.status()
            );

            assertEquals(
                0,
                first.attemptCount()
            );

            assertEquals(
                1L,
                countJobs(
                    connection,
                    "collect:" + runId
                )
            );

        } finally {

            deleteProcessingData(
                config,
                runId,
                jobId
            );
        }
    }

    @Test
    void shouldClaimJobAndMarkItSucceeded()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long runId =
            0L;

        long jobId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            runId =
                insertProcessingRun(
                    connection
                );

            JdbcProcessingJobQueueAdapter adapter =
                new JdbcProcessingJobQueueAdapter(
                    connection
                );

            ProcessingJob enqueued =
                adapter.enqueue(
                    ProcessingJobSubmission.collectDeals(
                        runId,
                        "claim:" + runId,
                        5,
                        BASE_TIME
                    )
                );

            jobId =
                enqueued.id();

            OffsetDateTime claimedAt =
                BASE_TIME.plusMinutes(
                    1
                );

            ProcessingJob claimed =
                adapter.claimNext(
                        "worker-claim",
                        claimedAt
                    )
                    .orElseThrow();

            assertEquals(
                jobId,
                claimed.id()
            );

            assertEquals(
                ProcessingJobStatus.RUNNING,
                claimed.status()
            );

            assertEquals(
                1,
                claimed.attemptCount()
            );

            assertSameInstant(
                claimedAt,
                claimed.lockedAt()
            );

            assertEquals(
                "worker-claim",
                claimed.lockedBy()
            );

            OffsetDateTime finishedAt =
                BASE_TIME.plusMinutes(
                    2
                );

            ProcessingJob succeeded =
                adapter.markSucceeded(
                    jobId,
                    "worker-claim",
                    finishedAt
                );

            assertEquals(
                ProcessingJobStatus.SUCCEEDED,
                succeeded.status()
            );

            assertNull(
                succeeded.lockedAt()
            );

            assertNull(
                succeeded.lockedBy()
            );

            assertSameInstant(
                finishedAt,
                succeeded.finishedAt()
            );

            assertTrue(
                adapter.claimNext(
                    "worker-other",
                    finishedAt.plusMinutes(
                        1
                    )
                ).isEmpty()
            );

        } finally {

            deleteProcessingData(
                config,
                runId,
                jobId
            );
        }
    }

    @Test
    void shouldSkipJobLockedByAnotherWorker()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long firstRunId =
            0L;

        long secondRunId =
            0L;

        long firstJobId =
            0L;

        long secondJobId =
            0L;

        try {

            try (Connection setupConnection =
                     DatabaseConnection.open(
                         config
                     )) {

                firstRunId =
                    insertProcessingRun(
                        setupConnection
                    );

                secondRunId =
                    insertProcessingRun(
                        setupConnection
                    );

                JdbcProcessingJobQueueAdapter setupAdapter =
                    new JdbcProcessingJobQueueAdapter(
                        setupConnection
                    );

                ProcessingJob first =
                    setupAdapter.enqueue(
                        ProcessingJobSubmission.collectDeals(
                            firstRunId,
                            "concurrent:" + firstRunId,
                            5,
                            BASE_TIME
                        )
                    );

                ProcessingJob second =
                    setupAdapter.enqueue(
                        ProcessingJobSubmission.collectDeals(
                            secondRunId,
                            "concurrent:" + secondRunId,
                            5,
                            BASE_TIME
                        )
                    );

                firstJobId =
                    first.id();

                secondJobId =
                    second.id();
            }

            try (Connection firstConnection =
                     DatabaseConnection.open(
                         config
                     );
                 Connection secondConnection =
                     DatabaseConnection.open(
                         config
                     )) {

                firstConnection.setAutoCommit(
                    false
                );

                JdbcProcessingJobQueueAdapter firstAdapter =
                    new JdbcProcessingJobQueueAdapter(
                        firstConnection
                    );

                JdbcProcessingJobQueueAdapter secondAdapter =
                    new JdbcProcessingJobQueueAdapter(
                        secondConnection
                    );

                ProcessingJob workerOneJob =
                    firstAdapter.claimNext(
                            "worker-1",
                            BASE_TIME.plusMinutes(
                                1
                            )
                        )
                        .orElseThrow();

                ProcessingJob workerTwoJob =
                    secondAdapter.claimNext(
                            "worker-2",
                            BASE_TIME.plusMinutes(
                                1
                            )
                        )
                        .orElseThrow();

                assertNotEquals(
                    workerOneJob.id(),
                    workerTwoJob.id()
                );

                assertEquals(
                    "worker-1",
                    workerOneJob.lockedBy()
                );

                assertEquals(
                    "worker-2",
                    workerTwoJob.lockedBy()
                );

                firstConnection.commit();

                firstConnection.setAutoCommit(
                    true
                );
            }

        } finally {

            deleteProcessingData(
                config,
                firstRunId,
                firstJobId
            );

            deleteProcessingData(
                config,
                secondRunId,
                secondJobId
            );
        }
    }

    @Test
    void shouldScheduleTransientFailureAndClaimAgain()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long runId =
            0L;

        long jobId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            runId =
                insertProcessingRun(
                    connection
                );

            JdbcProcessingJobQueueAdapter adapter =
                new JdbcProcessingJobQueueAdapter(
                    connection
                );

            ProcessingJob enqueued =
                adapter.enqueue(
                    ProcessingJobSubmission.collectDeals(
                        runId,
                        "retry:" + runId,
                        5,
                        BASE_TIME
                    )
                );

            jobId =
                enqueued.id();

            ProcessingJob firstAttempt =
                adapter.claimNext(
                        "worker-retry",
                        BASE_TIME.plusMinutes(
                            1
                        )
                    )
                    .orElseThrow();

            assertEquals(
                1,
                firstAttempt.attemptCount()
            );

            OffsetDateTime failedAt =
                BASE_TIME.plusMinutes(
                    2
                );

            OffsetDateTime availableAt =
                BASE_TIME.plusMinutes(
                    5
                );

            ProcessingFailure failure =
                new ProcessingFailure(
                    ProcessingFailureType.TRANSIENT,
                    "HTTP_503",
                    "Service temporarily unavailable"
                );

            ProcessingJob retryWaiting =
                adapter.scheduleRetry(
                    jobId,
                    "worker-retry",
                    failure,
                    availableAt,
                    failedAt
                );

            assertEquals(
                ProcessingJobStatus.RETRY_WAIT,
                retryWaiting.status()
            );

            assertEquals(
                ProcessingFailureType.TRANSIENT,
                retryWaiting.lastFailureType()
            );

            assertEquals(
                "HTTP_503",
                retryWaiting.lastErrorCode()
            );

            assertNull(
                retryWaiting.lockedAt()
            );

            assertNull(
                retryWaiting.lockedBy()
            );

            assertSameInstant(
                availableAt,
                retryWaiting.availableAt()
            );

            Optional<ProcessingJob> tooEarly =
                adapter.claimNext(
                    "worker-retry",
                    availableAt.minusSeconds(
                        1
                    )
                );

            assertTrue(
                tooEarly.isEmpty()
            );

            ProcessingJob secondAttempt =
                adapter.claimNext(
                        "worker-retry",
                        availableAt
                    )
                    .orElseThrow();

            assertEquals(
                ProcessingJobStatus.RUNNING,
                secondAttempt.status()
            );

            assertEquals(
                2,
                secondAttempt.attemptCount()
            );

            adapter.markSucceeded(
                jobId,
                "worker-retry",
                availableAt.plusMinutes(
                    1
                )
            );

        } finally {

            deleteProcessingData(
                config,
                runId,
                jobId
            );
        }
    }

    @Test
    void shouldMarkPermanentFailureAsDead()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long runId =
            0L;

        long jobId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            runId =
                insertProcessingRun(
                    connection
                );

            JdbcProcessingJobQueueAdapter adapter =
                new JdbcProcessingJobQueueAdapter(
                    connection
                );

            ProcessingJob enqueued =
                adapter.enqueue(
                    ProcessingJobSubmission.collectDeals(
                        runId,
                        "dead:" + runId,
                        5,
                        BASE_TIME
                    )
                );

            jobId =
                enqueued.id();

            adapter.claimNext(
                    "worker-dead",
                    BASE_TIME.plusMinutes(
                        1
                    )
                )
                .orElseThrow();

            OffsetDateTime failedAt =
                BASE_TIME.plusMinutes(
                    2
                );

            ProcessingFailure failure =
                new ProcessingFailure(
                    ProcessingFailureType.PERMANENT,
                    "INVALID_INPUT",
                    "Input cannot be processed"
                );

            ProcessingJob dead =
                adapter.markDead(
                    jobId,
                    "worker-dead",
                    failure,
                    failedAt
                );

            assertEquals(
                ProcessingJobStatus.DEAD,
                dead.status()
            );

            assertEquals(
                ProcessingFailureType.PERMANENT,
                dead.lastFailureType()
            );

            assertEquals(
                "INVALID_INPUT",
                dead.lastErrorCode()
            );

            assertSameInstant(
                failedAt,
                dead.finishedAt()
            );

            assertNull(
                dead.lockedAt()
            );

            assertNull(
                dead.lockedBy()
            );

            assertTrue(
                dead.finished()
            );

        } finally {

            deleteProcessingData(
                config,
                runId,
                jobId
            );
        }
    }

    @Test
    void shouldRejectRetryWhenMaximumAttemptsIsExhausted()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long runId =
            0L;

        long jobId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            runId =
                insertProcessingRun(
                    connection
                );

            JdbcProcessingJobQueueAdapter adapter =
                new JdbcProcessingJobQueueAdapter(
                    connection
                );

            ProcessingJob enqueued =
                adapter.enqueue(
                    ProcessingJobSubmission.collectDeals(
                        runId,
                        "exhausted:" + runId,
                        1,
                        BASE_TIME
                    )
                );

            jobId =
                enqueued.id();

            ProcessingJob claimed =
                adapter.claimNext(
                        "worker-exhausted",
                        BASE_TIME.plusMinutes(
                            1
                        )
                    )
                    .orElseThrow();

            assertEquals(
                1,
                claimed.attemptCount()
            );

            assertFalse(
                claimed.canRetry()
            );

            ProcessingFailure failure =
                new ProcessingFailure(
                    ProcessingFailureType.TRANSIENT,
                    "TIMEOUT",
                    "Controlled timeout"
                );

            assertThrows(
                IllegalStateException.class,
                () -> adapter.scheduleRetry(
                    claimed.id(),
                    "worker-exhausted",
                    failure,
                    BASE_TIME.plusMinutes(
                        5
                    ),
                    BASE_TIME.plusMinutes(
                        2
                    )
                )
            );

            ProcessingJob dead =
                adapter.markDead(
                    claimed.id(),
                    "worker-exhausted",
                    failure,
                    BASE_TIME.plusMinutes(
                        2
                    )
                );

            assertEquals(
                ProcessingJobStatus.DEAD,
                dead.status()
            );

        } finally {

            deleteProcessingData(
                config,
                runId,
                jobId
            );
        }
    }

    private static void assertSameInstant(
        OffsetDateTime expected,
        OffsetDateTime actual
    ) {

        assertEquals(
            expected.toInstant(),
            actual.toInstant()
        );
    }

    private long insertProcessingRun(
        Connection connection
    ) throws Exception {

        String runKey =
            "test:"
                + UUID.randomUUID();

        String sql =
            """
            INSERT INTO processing_run (
                run_key,
                source_uri,
                status,
                requested_at
            )
            VALUES (
                ?,
                ?,
                'PENDING',
                ?
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                runKey
            );

            statement.setString(
                2,
                "https://example.invalid/deals"
            );

            statement.setObject(
                3,
                BASE_TIME
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long countJobs(
        Connection connection,
        String idempotencyKey
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM processing_job
            WHERE idempotency_key = ?
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

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    private void deleteProcessingData(
        ApplicationConfig config,
        long runId,
        long jobId
    ) {

        if (runId <= 0
            && jobId <= 0) {

            return;
        }

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            if (jobId > 0) {

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM processing_job
                             WHERE id = ?
                             """
                         )) {

                    statement.setLong(
                        1,
                        jobId
                    );

                    statement.executeUpdate();
                }
            }

            if (runId > 0) {

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM processing_job
                             WHERE processing_run_id = ?
                             """
                         )) {

                    statement.setLong(
                        1,
                        runId
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
                        runId
                    );

                    statement.executeUpdate();
                }
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean processing queue test data",
                exception
            );
        }
    }
}
