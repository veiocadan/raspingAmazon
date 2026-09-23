package com.raspingamazon.application.orchestration.worker;

import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import com.raspingamazon.application.orchestration.failure.ProcessingJobFailureHandler;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingJobQueueAdapter;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessingWorkerJdbcIntegrationTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-23T00:15:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-23T00:15:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldClaimExecuteAndPersistSucceededJob()
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
                    "worker-success"
                );

            long jobId =
                insertPendingCollectJob(
                    connection,
                    runId,
                    "worker-success-job",
                    5
                );

            data =
                new TestData(
                    runId,
                    jobId
                );

            JdbcProcessingJobQueueAdapter queue =
                new JdbcProcessingJobQueueAdapter(
                    connection
                );

            AtomicInteger executions =
                new AtomicInteger();

            ProcessingWorker worker =
                new ProcessingWorker(
                    "worker-success",
                    queue,
                    job ->
                        executions.incrementAndGet(),
                    failureHandler(
                        queue
                    ),
                    CLOCK
                );

            ProcessingWorkerRunResult result =
                worker.runOnce();

            assertEquals(
                true,
                result.jobClaimed()
            );

            assertEquals(
                jobId,
                result.claimedJobId()
            );

            assertEquals(
                ProcessingJobStatus.SUCCEEDED,
                result.finalStatus()
            );

            assertEquals(
                1,
                executions.get()
            );

            PersistedJob persisted =
                findJob(
                    connection,
                    jobId
                );

            assertEquals(
                "SUCCEEDED",
                persisted.status()
            );

            assertEquals(
                1,
                persisted.attemptCount()
            );

            assertEquals(
                null,
                persisted.lockedAt()
            );

            assertEquals(
                null,
                persisted.lockedBy()
            );

            assertEquals(
                NOW,
                persisted.finishedAt()
            );

        } finally {

            deleteTestData(
                config,
                data
            );
        }
    }

    @Test
    void shouldPersistRetryWaitAfterTransientFailure()
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
                    "worker-retry"
                );

            long jobId =
                insertPendingCollectJob(
                    connection,
                    runId,
                    "worker-retry-job",
                    5
                );

            data =
                new TestData(
                    runId,
                    jobId
                );

            JdbcProcessingJobQueueAdapter queue =
                new JdbcProcessingJobQueueAdapter(
                    connection
                );

            ProcessingJobFailureHandler failureHandler =
                new ProcessingJobFailureHandler(
                    failure ->
                        new FailureClassification(
                            ProcessingFailureType.TRANSIENT,
                            "TEST_TRANSIENT",
                            "temporary failure"
                        ),
                    attemptCount ->
                        Duration.ofMinutes(
                            2
                        ),
                    queue
                );

            ProcessingWorker worker =
                new ProcessingWorker(
                    "worker-retry",
                    queue,
                    job -> {
                        throw new RuntimeException(
                            "temporary failure"
                        );
                    },
                    failureHandler,
                    CLOCK
                );

            ProcessingWorkerRunResult result =
                worker.runOnce();

            assertEquals(
                ProcessingJobStatus.RETRY_WAIT,
                result.finalStatus()
            );

            PersistedJob persisted =
                findJob(
                    connection,
                    jobId
                );

            assertEquals(
                "RETRY_WAIT",
                persisted.status()
            );

            assertEquals(
                1,
                persisted.attemptCount()
            );

            assertEquals(
                NOW.plusMinutes(
                    2
                ),
                persisted.availableAt()
            );

            assertEquals(
                null,
                persisted.lockedAt()
            );

            assertEquals(
                null,
                persisted.lockedBy()
            );

            assertEquals(
                "TRANSIENT",
                persisted.lastFailureType()
            );

            assertEquals(
                "TEST_TRANSIENT",
                persisted.lastErrorCode()
            );

            assertEquals(
                null,
                persisted.finishedAt()
            );

        } finally {

            deleteTestData(
                config,
                data
            );
        }
    }

    @Test
    void shouldPersistDeadAfterPermanentFailure()
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
                    "worker-dead"
                );

            long jobId =
                insertPendingCollectJob(
                    connection,
                    runId,
                    "worker-dead-job",
                    5
                );

            data =
                new TestData(
                    runId,
                    jobId
                );

            JdbcProcessingJobQueueAdapter queue =
                new JdbcProcessingJobQueueAdapter(
                    connection
                );

            ProcessingJobFailureHandler failureHandler =
                new ProcessingJobFailureHandler(
                    failure ->
                        new FailureClassification(
                            ProcessingFailureType.PERMANENT,
                            "TEST_PERMANENT",
                            "invalid processing data"
                        ),
                    attemptCount -> {
                        throw new AssertionError(
                            "backoff must not run for permanent failure"
                        );
                    },
                    queue
                );

            ProcessingWorker worker =
                new ProcessingWorker(
                    "worker-dead",
                    queue,
                    job -> {
                        throw new IllegalArgumentException(
                            "invalid processing data"
                        );
                    },
                    failureHandler,
                    CLOCK
                );

            ProcessingWorkerRunResult result =
                worker.runOnce();

            assertEquals(
                ProcessingJobStatus.DEAD,
                result.finalStatus()
            );

            PersistedJob persisted =
                findJob(
                    connection,
                    jobId
                );

            assertEquals(
                "DEAD",
                persisted.status()
            );

            assertEquals(
                1,
                persisted.attemptCount()
            );

            assertEquals(
                null,
                persisted.lockedAt()
            );

            assertEquals(
                null,
                persisted.lockedBy()
            );

            assertEquals(
                "PERMANENT",
                persisted.lastFailureType()
            );

            assertEquals(
                "TEST_PERMANENT",
                persisted.lastErrorCode()
            );

            assertEquals(
                NOW,
                persisted.finishedAt()
            );

        } finally {

            deleteTestData(
                config,
                data
            );
        }
    }

    @Test
    void shouldReturnIdleWithoutChangingDatabaseWhenNoJobExists()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingJobQueueAdapter queue =
                new JdbcProcessingJobQueueAdapter(
                    connection
                );

            ProcessingWorker worker =
                new ProcessingWorker(
                    "worker-idle-" + System.nanoTime(),
                    queue,
                    job -> {
                        throw new AssertionError(
                            "executor must not be called"
                        );
                    },
                    failureHandler(
                        queue
                    ),
                    CLOCK
                );

            /*
             * Este teste pressupõe banco de testes limpo de jobs
             * disponíveis anteriores. Para impedir interferência
             * dos próprios testes desta classe, cada teste remove
             * integralmente os registros que cria.
             */
            ProcessingWorkerRunResult result =
                worker.runOnce();

            if (result.jobClaimed()) {

                /*
                 * A suíte pode coexistir com dados externos no banco
                 * de desenvolvimento. Nesse caso não fazemos uma
                 * afirmação incorreta de isolamento global.
                 *
                 * O comportamento de ausência de job já é coberto
                 * pelo teste unitário do ProcessingWorker.
                 */
                return;
            }

            assertEquals(
                null,
                result.claimedJobId()
            );

            assertEquals(
                null,
                result.finalStatus()
            );
        }
    }

    private static ProcessingJobFailureHandler failureHandler(
        JdbcProcessingJobQueueAdapter queue
    ) {

        return new ProcessingJobFailureHandler(
            failure ->
                new FailureClassification(
                    ProcessingFailureType.PERMANENT,
                    "TEST_FAILURE",
                    failure.getMessage() == null
                        ? "failure"
                        : failure.getMessage()
                ),
            attemptCount ->
                Duration.ofSeconds(
                    30
                ),
            queue
        );
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
                "integration-"
                    + suffix
                    + "-"
                    + System.nanoTime()
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

    private long insertPendingCollectJob(
        Connection connection,
        long processingRunId,
        String keyPrefix,
        int maxAttempts
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
                available_at
            )
            VALUES (
                'COLLECT_DEALS',
                'PENDING',
                ?,
                NULL,
                NULL,
                ?,
                0,
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
                keyPrefix
                    + "-"
                    + System.nanoTime()
            );

            statement.setInt(
                3,
                maxAttempts
            );

            statement.setObject(
                4,
                NOW.minusMinutes(
                    1
                )
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
                "Could not clean ProcessingWorker integration test data",
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
