package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.reprocess.ProcessingJobReprocessRequest;
import com.raspingamazon.application.orchestration.reprocess.ProcessingJobReprocessResult;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcProcessingJobReprocessAdapterTest {

    private static final OffsetDateTime BASE_TIME =
        OffsetDateTime.parse(
            "2026-10-04T03:30:00Z"
        );

    @Test
    void shouldReopenDeadJobAndPersistAuditSnapshot()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        Fixture fixture =
            createDeadJob(
                config,
                3
            );

        try {

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingJobReprocessAdapter adapter =
                    new JdbcProcessingJobReprocessAdapter(
                        connection
                    );

                OffsetDateTime requestedAt =
                    BASE_TIME.plusMinutes(
                        10
                    );

                ProcessingJobReprocessResult result =
                    adapter.reprocess(
                        request(
                            fixture.jobId(),
                            "request:"
                                + UUID.randomUUID()
                        ),
                        requestedAt
                    );

                assertTrue(
                    result.newlyApplied()
                );

                assertEquals(
                    fixture.jobId(),
                    result.job()
                        .id()
                );

                assertEquals(
                    ProcessingJobStatus.PENDING,
                    result.job()
                        .status()
                );

                assertEquals(
                    0,
                    result.job()
                        .attemptCount()
                );

                assertEquals(
                    fixture.maxAttempts(),
                    result.job()
                        .maxAttempts()
                );

                assertSameInstant(
                    requestedAt,
                    result.job()
                        .availableAt()
                );

                assertNull(
                    result.job()
                        .lockedAt()
                );

                assertNull(
                    result.job()
                        .lockedBy()
                );

                assertNull(
                    result.job()
                        .lastFailureType()
                );

                assertNull(
                    result.job()
                        .lastErrorCode()
                );

                assertNull(
                    result.job()
                        .lastErrorMessage()
                );

                assertNull(
                    result.job()
                        .finishedAt()
                );

                AuditRow audit =
                    readAudit(
                        connection,
                        result.reprocessEventId()
                    );

                assertEquals(
                    fixture.jobId(),
                    audit.processingJobId()
                );

                assertEquals(
                    "operator-test",
                    audit.requestedBy()
                );

                assertEquals(
                    "controlled manual recovery",
                    audit.reason()
                );

                assertEquals(
                    "DEAD",
                    audit.previousStatus()
                );

                assertEquals(
                    fixture.deadAttemptCount(),
                    audit.previousAttemptCount()
                );

                assertEquals(
                    fixture.maxAttempts(),
                    audit.previousMaxAttempts()
                );

                assertEquals(
                    "PERMANENT",
                    audit.previousFailureType()
                );

                assertEquals(
                    "CONTROLLED_PERMANENT_FAILURE",
                    audit.previousErrorCode()
                );

                assertEquals(
                    "controlled failure before manual reprocessing",
                    audit.previousErrorMessage()
                );

                assertSameInstant(
                    fixture.deadFinishedAt(),
                    audit.previousFinishedAt()
                );

                assertSameInstant(
                    requestedAt,
                    audit.requestedAt()
                );
            }

        } finally {

            deleteFixture(
                config,
                fixture
            );
        }
    }

    @Test
    void shouldReplaySameRequestIdempotentlyEvenAfterJobWasClaimedAgain()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        Fixture fixture =
            createDeadJob(
                config,
                3
            );

        String requestKey =
            "request:"
                + UUID.randomUUID();

        try {

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingJobReprocessAdapter reprocessAdapter =
                    new JdbcProcessingJobReprocessAdapter(
                        connection
                    );

                ProcessingJobReprocessRequest request =
                    request(
                        fixture.jobId(),
                        requestKey
                    );

                ProcessingJobReprocessResult first =
                    reprocessAdapter.reprocess(
                        request,
                        BASE_TIME.plusMinutes(
                            10
                        )
                    );

                JdbcProcessingJobQueueAdapter queue =
                    new JdbcProcessingJobQueueAdapter(
                        connection
                    );

                ProcessingJob claimed =
                    queue.claimNext(
                            "worker-after-reprocess",
                            BASE_TIME.plusMinutes(
                                11
                            )
                        )
                        .orElseThrow();

                assertEquals(
                    fixture.jobId(),
                    claimed.id()
                );

                assertEquals(
                    ProcessingJobStatus.RUNNING,
                    claimed.status()
                );

                ProcessingJobReprocessResult replay =
                    reprocessAdapter.reprocess(
                        request,
                        BASE_TIME.plusMinutes(
                            12
                        )
                    );

                assertFalse(
                    replay.newlyApplied()
                );

                assertEquals(
                    first.reprocessEventId(),
                    replay.reprocessEventId()
                );

                assertEquals(
                    ProcessingJobStatus.RUNNING,
                    replay.job()
                        .status()
                );

                assertEquals(
                    1,
                    replay.job()
                        .attemptCount()
                );

                assertEquals(
                    1L,
                    countEvents(
                        connection,
                        requestKey
                    )
                );

                queue.markSucceeded(
                    fixture.jobId(),
                    "worker-after-reprocess",
                    BASE_TIME.plusMinutes(
                        13
                    )
                );
            }

        } finally {

            deleteFixture(
                config,
                fixture
            );
        }
    }

    @Test
    void shouldRejectNewRequestForNonDeadJob()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        long runId =
            0L;

        long jobId =
            0L;

        try {

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                runId =
                    insertProcessingRun(
                        connection
                    );

                JdbcProcessingJobQueueAdapter queue =
                    new JdbcProcessingJobQueueAdapter(
                        connection
                    );

                ProcessingJob pending =
                    queue.enqueue(
                        ProcessingJobSubmission.collectDeals(
                            runId,
                            "pending:"
                                + runId,
                            3,
                            BASE_TIME
                        )
                    );

                jobId =
                    pending.id();

                JdbcProcessingJobReprocessAdapter adapter =
                    new JdbcProcessingJobReprocessAdapter(
                        connection
                    );

                ProcessingJobReprocessRequest request =
                    request(
                        jobId,
                        "request:"
                            + UUID.randomUUID()
                    );

                IllegalStateException exception =
                    assertThrows(
                        IllegalStateException.class,
                        () ->
                            adapter.reprocess(
                                request,
                                BASE_TIME.plusMinutes(
                                    10
                                )
                            )
                    );

                assertTrue(
                    exception.getMessage()
                        .contains(
                            "status is PENDING"
                        )
                );

                assertEquals(
                    0L,
                    countEventsForJob(
                        connection,
                        jobId
                    )
                );
            }

        } finally {

            deleteProcessingData(
                config,
                runId,
                jobId
            );
        }
    }

    @Test
    void shouldRejectRequestKeyCollisionAcrossJobs()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        Fixture first =
            createDeadJob(
                config,
                3
            );

        Fixture second =
            createDeadJob(
                config,
                3
            );

        String requestKey =
            "request:"
                + UUID.randomUUID();

        try {

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingJobReprocessAdapter adapter =
                    new JdbcProcessingJobReprocessAdapter(
                        connection
                    );

                adapter.reprocess(
                    request(
                        first.jobId(),
                        requestKey
                    ),
                    BASE_TIME.plusMinutes(
                        10
                    )
                );

                IllegalStateException exception =
                    assertThrows(
                        IllegalStateException.class,
                        () ->
                            adapter.reprocess(
                                request(
                                    second.jobId(),
                                    requestKey
                                ),
                                BASE_TIME.plusMinutes(
                                    11
                                )
                            )
                    );

                assertTrue(
                    exception.getMessage()
                        .contains(
                            "requestKey collision"
                        )
                );

                ProcessingJob secondPersisted =
                    readJob(
                        connection,
                        second.jobId()
                    );

                assertEquals(
                    ProcessingJobStatus.DEAD,
                    secondPersisted.status()
                );

                assertEquals(
                    1L,
                    countEvents(
                        connection,
                        requestKey
                    )
                );
            }

        } finally {

            deleteFixture(
                config,
                first
            );

            deleteFixture(
                config,
                second
            );
        }
    }

    @Test
    void shouldAllowASecondControlledReprocessAfterJobDiesAgain()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        Fixture fixture =
            createDeadJob(
                config,
                3
            );

        try {

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingJobReprocessAdapter reprocessAdapter =
                    new JdbcProcessingJobReprocessAdapter(
                        connection
                    );

                JdbcProcessingJobQueueAdapter queue =
                    new JdbcProcessingJobQueueAdapter(
                        connection
                    );

                reprocessAdapter.reprocess(
                    request(
                        fixture.jobId(),
                        "request:"
                            + UUID.randomUUID()
                    ),
                    BASE_TIME.plusMinutes(
                        10
                    )
                );

                queue.claimNext(
                        "worker-second-cycle",
                        BASE_TIME.plusMinutes(
                            11
                        )
                    )
                    .orElseThrow();

                queue.markDead(
                    fixture.jobId(),
                    "worker-second-cycle",
                    new ProcessingFailure(
                        ProcessingFailureType.PERMANENT,
                        "SECOND_FAILURE",
                        "second controlled failure"
                    ),
                    BASE_TIME.plusMinutes(
                        12
                    )
                );

                ProcessingJobReprocessResult second =
                    reprocessAdapter.reprocess(
                        request(
                            fixture.jobId(),
                            "request:"
                                + UUID.randomUUID()
                        ),
                        BASE_TIME.plusMinutes(
                            13
                        )
                    );

                assertTrue(
                    second.newlyApplied()
                );

                assertEquals(
                    ProcessingJobStatus.PENDING,
                    second.job()
                        .status()
                );

                assertEquals(
                    0,
                    second.job()
                        .attemptCount()
                );

                assertEquals(
                    2L,
                    countEventsForJob(
                        connection,
                        fixture.jobId()
                    )
                );
            }

        } finally {

            deleteFixture(
                config,
                fixture
            );
        }
    }

    @Test
    void shouldApplySameConcurrentRequestOnlyOnce()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        Fixture fixture =
            createDeadJob(
                config,
                3
            );

        String requestKey =
            "request:"
                + UUID.randomUUID();

        CountDownLatch ready =
            new CountDownLatch(
                2
            );

        CountDownLatch start =
            new CountDownLatch(
                1
            );

        ExecutorService executor =
            Executors.newFixedThreadPool(
                2
            );

        try {

            Future<ProcessingJobReprocessResult> firstFuture =
                executor.submit(
                    () ->
                        concurrentReprocess(
                            config,
                            fixture.jobId(),
                            requestKey,
                            ready,
                            start
                        )
                );

            Future<ProcessingJobReprocessResult> secondFuture =
                executor.submit(
                    () ->
                        concurrentReprocess(
                            config,
                            fixture.jobId(),
                            requestKey,
                            ready,
                            start
                        )
                );

            assertTrue(
                ready.await(
                    10,
                    TimeUnit.SECONDS
                )
            );

            start.countDown();

            ProcessingJobReprocessResult first =
                firstFuture.get(
                    10,
                    TimeUnit.SECONDS
                );

            ProcessingJobReprocessResult second =
                secondFuture.get(
                    10,
                    TimeUnit.SECONDS
                );

            assertEquals(
                first.reprocessEventId(),
                second.reprocessEventId()
            );

            int newlyAppliedCount =
                (first.newlyApplied()
                    ? 1
                    : 0)
                    + (second.newlyApplied()
                    ? 1
                    : 0);

            assertEquals(
                1,
                newlyAppliedCount
            );

            try (Connection verification =
                     DatabaseConnection.open(
                         config
                     )) {

                assertEquals(
                    1L,
                    countEvents(
                        verification,
                        requestKey
                    )
                );

                ProcessingJob job =
                    readJob(
                        verification,
                        fixture.jobId()
                    );

                assertEquals(
                    ProcessingJobStatus.PENDING,
                    job.status()
                );

                assertEquals(
                    0,
                    job.attemptCount()
                );
            }

        } finally {

            executor.shutdownNow();

            deleteFixture(
                config,
                fixture
            );
        }
    }

    private ProcessingJobReprocessResult concurrentReprocess(
        ApplicationConfig config,
        long jobId,
        String requestKey,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingJobReprocessAdapter adapter =
                new JdbcProcessingJobReprocessAdapter(
                    connection
                );

            ready.countDown();
            start.await();

            return adapter.reprocess(
                request(
                    jobId,
                    requestKey
                ),
                BASE_TIME.plusMinutes(
                    10
                )
            );
        }
    }

    private Fixture createDeadJob(
        ApplicationConfig config,
        int maxAttempts
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            long runId =
                insertProcessingRun(
                    connection
                );

            JdbcProcessingJobQueueAdapter queue =
                new JdbcProcessingJobQueueAdapter(
                    connection
                );

            ProcessingJob pending =
                queue.enqueue(
                    ProcessingJobSubmission.collectDeals(
                        runId,
                        "dead-letter:"
                            + runId,
                        maxAttempts,
                        BASE_TIME
                    )
                );

            ProcessingJob running =
                queue.claimNext(
                        "worker-dead-letter",
                        BASE_TIME.plusMinutes(
                            1
                        )
                    )
                    .orElseThrow();

            OffsetDateTime deadAt =
                BASE_TIME.plusMinutes(
                    2
                );

            ProcessingJob dead =
                queue.markDead(
                    running.id(),
                    "worker-dead-letter",
                    new ProcessingFailure(
                        ProcessingFailureType.PERMANENT,
                        "CONTROLLED_PERMANENT_FAILURE",
                        "controlled failure before manual reprocessing"
                    ),
                    deadAt
                );

            return new Fixture(
                runId,
                pending.id(),
                dead.attemptCount(),
                dead.maxAttempts(),
                deadAt
            );
        }
    }

    private ProcessingJobReprocessRequest request(
        long jobId,
        String requestKey
    ) {

        return new ProcessingJobReprocessRequest(
            jobId,
            requestKey,
            "operator-test",
            "controlled manual recovery"
        );
    }

    private long insertProcessingRun(
        Connection connection
    ) throws Exception {

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
                "reprocess-test:"
                    + UUID.randomUUID()
            );

            statement.setString(
                2,
                "https://example.com/reprocess-test"
            );

            statement.setObject(
                3,
                BASE_TIME
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

    private ProcessingJob readJob(
        Connection connection,
        long jobId
    ) throws Exception {

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

                resultSet.next();

                return new ProcessingJob(
                    resultSet.getLong(
                        "id"
                    ),
                    com.raspingamazon.application.orchestration.ProcessingJobType.valueOf(
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
                    nullableFailureType(
                        resultSet.getString(
                            "last_failure_type"
                        )
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
        }
    }

    private AuditRow readAudit(
        Connection connection,
        long eventId
    ) throws Exception {

        String sql =
            """
            SELECT
                processing_job_id,
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
            FROM processing_job_reprocess_event
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                eventId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return new AuditRow(
                    resultSet.getLong(
                        "processing_job_id"
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
                    ),
                    resultSet.getString(
                        "previous_status"
                    ),
                    resultSet.getInt(
                        "previous_attempt_count"
                    ),
                    resultSet.getInt(
                        "previous_max_attempts"
                    ),
                    resultSet.getString(
                        "previous_failure_type"
                    ),
                    resultSet.getString(
                        "previous_error_code"
                    ),
                    resultSet.getString(
                        "previous_error_message"
                    ),
                    resultSet.getObject(
                        "previous_finished_at",
                        OffsetDateTime.class
                    )
                );
            }
        }
    }

    private long countEvents(
        Connection connection,
        String requestKey
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
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

                resultSet.next();

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    private long countEventsForJob(
        Connection connection,
        long jobId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM processing_job_reprocess_event
            WHERE processing_job_id = ?
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

                resultSet.next();

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    private void deleteFixture(
        ApplicationConfig config,
        Fixture fixture
    ) throws Exception {

        deleteProcessingData(
            config,
            fixture.runId(),
            fixture.jobId()
        );
    }

    private void deleteProcessingData(
        ApplicationConfig config,
        long runId,
        long jobId
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            if (jobId > 0L) {

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM processing_job_reprocess_event
                             WHERE processing_job_id = ?
                             """
                         )) {

                    statement.setLong(
                        1,
                        jobId
                    );

                    statement.executeUpdate();
                }

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

            if (runId > 0L) {

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
        }
    }

    private static Long getNullableLong(
        ResultSet resultSet,
        String column
    ) throws Exception {

        long value =
            resultSet.getLong(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private static ProcessingFailureType nullableFailureType(
        String value
    ) {

        if (value == null) {
            return null;
        }

        return ProcessingFailureType.valueOf(
            value
        );
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

    private record Fixture(
        long runId,
        long jobId,
        int deadAttemptCount,
        int maxAttempts,
        OffsetDateTime deadFinishedAt
    ) {
    }

    private record AuditRow(
        long processingJobId,
        String requestedBy,
        String reason,
        OffsetDateTime requestedAt,
        String previousStatus,
        int previousAttemptCount,
        int previousMaxAttempts,
        String previousFailureType,
        String previousErrorCode,
        String previousErrorMessage,
        OffsetDateTime previousFinishedAt
    ) {
    }
}
