package com.raspingamazon.application.scheduling;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.shared.port.TransactionPort;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingJobQueueAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingRunRepositoryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingScheduleAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcTransactionAdapter;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova vertical do disparo agendado da FASE 17.
 *
 * <p>O teste utiliza PostgreSQL real e a composição concreta:</p>
 *
 * <pre>
 * ProcessingSchedule
 *      ↓
 * ScheduleProcessingRunUseCase
 *      ↓
 * ProcessingRun
 *      ↓
 * COLLECT_DEALS
 * </pre>
 *
 * <p>Também prova que lease, ProcessingRun, ProcessingJob e
 * confirmação do schedule pertencem à mesma unidade transacional.</p>
 */
@PostgresIntegrationTest
class ScheduleProcessingRunJdbcIntegrationTest {

    private static final URI SOURCE =
        URI.create(
            "https://www.amazon.com.br/deals"
        );

    private static final Duration INTERVAL =
        Duration.ofMinutes(
            15
        );

    private static final Duration LEASE_DURATION =
        Duration.ofMinutes(
            2
        );

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-27T16:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-27T16:00:00Z"
            ),
            ZoneOffset.UTC
        );

    private static final int MAX_ATTEMPTS =
        5;

    @Test
    void shouldCreateRunJobAndConfirmScheduleAtomically()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            newScheduleKey(
                "success"
            );

        String expectedRunKey =
            scheduledRunKey(
                scheduleKey,
                NOW
            );

        try {

            createSchedule(
                config,
                scheduleKey
            );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter scheduleAdapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                JdbcProcessingRunRepositoryAdapter runRepository =
                    new JdbcProcessingRunRepositoryAdapter(
                        connection
                    );

                JdbcProcessingJobQueueAdapter jobQueue =
                    new JdbcProcessingJobQueueAdapter(
                        connection
                    );

                TransactionPort transactionPort =
                    new JdbcTransactionAdapter(
                        connection
                    );

                ScheduleProcessingRunUseCase useCase =
                    new ScheduleProcessingRunUseCase(
                        scheduleAdapter,
                        runRepository,
                        jobQueue,
                        transactionPort,
                        CLOCK,
                        LEASE_DURATION,
                        MAX_ATTEMPTS
                    );

                /*
                 * Primeira execução:
                 *
                 * a janela está vencida e deve produzir exatamente
                 * uma ProcessingRun e um COLLECT_DEALS.
                 */
                ScheduledProcessingRun result =
                    useCase.execute(
                        scheduleKey,
                        "scheduler-integration-1"
                    ).orElseThrow();

                assertTrue(
                    result.processingRunId() > 0
                );

                assertTrue(
                    result.processingJobId() > 0
                );

                assertEquals(
                    scheduleKey,
                    result.scheduleKey()
                );

                assertSameInstant(
                    NOW,
                    result.scheduledFor()
                );

                assertSameInstant(
                    NOW.plus(
                        INTERVAL
                    ),
                    result.nextRunAt()
                );

                /*
                 * Segunda execução no mesmo instante.
                 *
                 * O primeiro ciclo já avançou nextRunAt para o futuro,
                 * portanto a mesma janela não pode gerar outra run.
                 */
                Optional<ScheduledProcessingRun> secondAttempt =
                    useCase.execute(
                        scheduleKey,
                        "scheduler-integration-1"
                    );

                assertTrue(
                    secondAttempt.isEmpty()
                );

                /*
                 * Confirma a ProcessingRun diretamente no banco.
                 */
                PersistedRun persistedRun =
                    findRun(
                        connection,
                        expectedRunKey
                    );

                assertNotNull(
                    persistedRun
                );

                assertEquals(
                    result.processingRunId(),
                    persistedRun.id()
                );

                assertEquals(
                    expectedRunKey,
                    persistedRun.runKey()
                );

                assertEquals(
                    SOURCE.toString(),
                    persistedRun.sourceUri()
                );

                assertEquals(
                    "PENDING",
                    persistedRun.status()
                );

                assertSameInstant(
                    NOW,
                    persistedRun.requestedAt()
                );

                /*
                 * Confirma que existe exatamente um job inicial da run.
                 */
                assertEquals(
                    1L,
                    countCollectJobsForRun(
                        connection,
                        result.processingRunId()
                    )
                );

                PersistedJob persistedJob =
                    findJob(
                        connection,
                        result.processingJobId()
                    );

                assertNotNull(
                    persistedJob
                );

                assertEquals(
                    "COLLECT_DEALS",
                    persistedJob.jobType()
                );

                assertEquals(
                    "PENDING",
                    persistedJob.status()
                );

                assertEquals(
                    result.processingRunId(),
                    persistedJob.processingRunId()
                );

                assertEquals(
                    "collect:"
                        + result.processingRunId(),
                    persistedJob.idempotencyKey()
                );

                assertEquals(
                    0,
                    persistedJob.attemptCount()
                );

                assertEquals(
                    MAX_ATTEMPTS,
                    persistedJob.maxAttempts()
                );

                assertSameInstant(
                    NOW,
                    persistedJob.availableAt()
                );

                /*
                 * Confirma o estado final do scheduler.
                 */
                ProcessingSchedule schedule =
                    scheduleAdapter.findByKey(
                        scheduleKey
                    ).orElseThrow();

                assertFalse(
                    schedule.paused()
                );

                assertFalse(
                    schedule.leased()
                );

                assertTrue(
                    schedule.hasLastExecution()
                );

                assertSameInstant(
                    NOW,
                    schedule.lastScheduledFor()
                );

                assertEquals(
                    result.processingRunId(),
                    schedule.lastProcessingRunId()
                );

                assertSameInstant(
                    NOW.plus(
                        INTERVAL
                    ),
                    schedule.nextRunAt()
                );

                /*
                 * A reentrada não pode ter criado run duplicada.
                 */
                assertEquals(
                    1L,
                    countRunsByRunKey(
                        connection,
                        expectedRunKey
                    )
                );
            }

        } finally {

            cleanup(
                config,
                scheduleKey,
                expectedRunKey
            );
        }
    }

    @Test
    void shouldRollbackLeaseRunAndJobWhenFailureOccursAfterEnqueue()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            newScheduleKey(
                "rollback"
            );

        String expectedRunKey =
            scheduledRunKey(
                scheduleKey,
                NOW
            );

        try {

            createSchedule(
                config,
                scheduleKey
            );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter scheduleAdapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                JdbcProcessingRunRepositoryAdapter runRepository =
                    new JdbcProcessingRunRepositoryAdapter(
                        connection
                    );

                JdbcProcessingJobQueueAdapter realJobQueue =
                    new JdbcProcessingJobQueueAdapter(
                        connection
                    );

                FailingAfterEnqueueQueue failingJobQueue =
                    new FailingAfterEnqueueQueue(
                        realJobQueue
                    );

                TransactionPort transactionPort =
                    new JdbcTransactionAdapter(
                        connection
                    );

                ScheduleProcessingRunUseCase useCase =
                    new ScheduleProcessingRunUseCase(
                        scheduleAdapter,
                        runRepository,
                        failingJobQueue,
                        transactionPort,
                        CLOCK,
                        LEASE_DURATION,
                        MAX_ATTEMPTS
                    );

                /*
                 * O wrapper executa o INSERT real do ProcessingJob e
                 * somente depois lança a falha proposital.
                 *
                 * Portanto, quando o TransactionPort recebe a exceção,
                 * lease, run e job já foram alterados dentro da
                 * transação e precisam ser todos revertidos.
                 */
                assertThrows(
                    ExpectedEnqueueFailure.class,
                    () -> useCase.execute(
                        scheduleKey,
                        "scheduler-integration-rollback"
                    )
                );

                /*
                 * O adapter transacional deve restaurar autoCommit
                 * depois do rollback.
                 */
                assertTrue(
                    connection.getAutoCommit()
                );

                assertTrue(
                    failingJobQueue.enqueueExecuted()
                );

                assertTrue(
                    failingJobQueue.enqueuedJobId() > 0
                );

                assertTrue(
                    failingJobQueue.processingRunId() > 0
                );

                /*
                 * A ProcessingRun criada antes da falha não pode
                 * permanecer no banco.
                 */
                assertEquals(
                    0L,
                    countRunsByRunKey(
                        connection,
                        expectedRunKey
                    )
                );

                assertEquals(
                    0L,
                    countRunsById(
                        connection,
                        failingJobQueue.processingRunId()
                    )
                );

                /*
                 * O ProcessingJob chegou a ser inserido pelo adapter
                 * real, porém precisa desaparecer com o rollback.
                 */
                assertEquals(
                    0L,
                    countJobsById(
                        connection,
                        failingJobQueue.enqueuedJobId()
                    )
                );

                /*
                 * A aquisição do lease também fazia parte da mesma
                 * transação.
                 *
                 * Depois do rollback, o schedule deve estar exatamente
                 * na janela original.
                 */
                ProcessingSchedule schedule =
                    scheduleAdapter.findByKey(
                        scheduleKey
                    ).orElseThrow();

                assertFalse(
                    schedule.leased()
                );

                assertNull(
                    schedule.leaseOwner()
                );

                assertNull(
                    schedule.leaseExpiresAt()
                );

                assertFalse(
                    schedule.hasLastExecution()
                );

                assertNull(
                    schedule.lastScheduledFor()
                );

                assertNull(
                    schedule.lastProcessingRunId()
                );

                assertSameInstant(
                    NOW,
                    schedule.nextRunAt()
                );

                /*
                 * Como a transação foi revertida, a mesma janela
                 * continua recuperável.
                 *
                 * Uma nova tentativa normal deve conseguir adquiri-la
                 * e concluí-la.
                 */
                ScheduleProcessingRunUseCase retryUseCase =
                    new ScheduleProcessingRunUseCase(
                        scheduleAdapter,
                        runRepository,
                        realJobQueue,
                        transactionPort,
                        CLOCK,
                        LEASE_DURATION,
                        MAX_ATTEMPTS
                    );

                ScheduledProcessingRun recovered =
                    retryUseCase.execute(
                        scheduleKey,
                        "scheduler-integration-recovery"
                    ).orElseThrow();

                assertTrue(
                    recovered.processingRunId() > 0
                );

                assertTrue(
                    recovered.processingJobId() > 0
                );

                assertEquals(
                    1L,
                    countRunsByRunKey(
                        connection,
                        expectedRunKey
                    )
                );

                assertEquals(
                    1L,
                    countCollectJobsForRun(
                        connection,
                        recovered.processingRunId()
                    )
                );
            }

        } finally {

            cleanup(
                config,
                scheduleKey,
                expectedRunKey
            );
        }
    }

    private void createSchedule(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingScheduleAdapter adapter =
                new JdbcProcessingScheduleAdapter(
                    connection
                );

            adapter.saveIfAbsent(
                new ProcessingSchedule(
                    scheduleKey,
                    SOURCE,
                    true,
                    INTERVAL,
                    NOW,
                    null,
                    null,
                    null,
                    null,
                    NOW.minusMinutes(
                        1
                    ),
                    NOW.minusMinutes(
                        1
                    )
                )
            );
        }
    }

    private PersistedRun findRun(
        Connection connection,
        String runKey
    ) throws Exception {

        String sql =
            """
            SELECT
                id,
                run_key,
                source_uri,
                status,
                requested_at
            FROM processing_run
            WHERE run_key = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                runKey
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return null;
                }

                return new PersistedRun(
                    resultSet.getLong(
                        "id"
                    ),
                    resultSet.getString(
                        "run_key"
                    ),
                    resultSet.getString(
                        "source_uri"
                    ),
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getObject(
                        "requested_at",
                        OffsetDateTime.class
                    )
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
                id,
                job_type,
                status,
                processing_run_id,
                idempotency_key,
                attempt_count,
                max_attempts,
                available_at
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
                    return null;
                }

                return new PersistedJob(
                    resultSet.getLong(
                        "id"
                    ),
                    resultSet.getString(
                        "job_type"
                    ),
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getLong(
                        "processing_run_id"
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
                    )
                );
            }
        }
    }

    private long countRunsByRunKey(
        Connection connection,
        String runKey
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM processing_run
            WHERE run_key = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                runKey
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "ProcessingRun count returned no row"
                    );
                }

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    private long countRunsById(
        Connection connection,
        long runId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM processing_run
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                runId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "ProcessingRun id count returned no row"
                    );
                }

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    private long countJobsById(
        Connection connection,
        long jobId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
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
                        "ProcessingJob id count returned no row"
                    );
                }

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    private long countCollectJobsForRun(
        Connection connection,
        long processingRunId
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM processing_job
            WHERE processing_run_id = ?
              AND job_type = 'COLLECT_DEALS'
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                processingRunId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "COLLECT_DEALS count returned no row"
                    );
                }

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    private String newScheduleKey(
        String purpose
    ) {

        return "fase17-trigger-"
            + purpose
            + "-"
            + UUID.randomUUID();
    }

    private String scheduledRunKey(
        String scheduleKey,
        OffsetDateTime scheduledFor
    ) {

        return "scheduled:"
            + scheduleKey
            + ":"
            + scheduledFor.toInstant();
    }

    private void assertSameInstant(
        OffsetDateTime expected,
        OffsetDateTime actual
    ) {

        assertEquals(
            expected.toInstant(),
            actual.toInstant()
        );
    }

    private void cleanup(
        ApplicationConfig config,
        String scheduleKey,
        String runKey
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            /*
             * processing_schedule pode possuir FK para a última run.
             * Portanto removemos primeiro o schedule.
             */
            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM processing_schedule
                         WHERE schedule_key = ?
                         """
                     )) {

                statement.setString(
                    1,
                    scheduleKey
                );

                statement.executeUpdate();
            }

            /*
             * Depois removemos todos os jobs pertencentes à run.
             */
            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM processing_job
                         WHERE processing_run_id IN (
                             SELECT id
                             FROM processing_run
                             WHERE run_key = ?
                         )
                         """
                     )) {

                statement.setString(
                    1,
                    runKey
                );

                statement.executeUpdate();
            }

            /*
             * Por último a ProcessingRun.
             */
            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM processing_run
                         WHERE run_key = ?
                         """
                     )) {

                statement.setString(
                    1,
                    runKey
                );

                statement.executeUpdate();
            }
        }
    }

    private record PersistedRun(
        long id,
        String runKey,
        String sourceUri,
        String status,
        OffsetDateTime requestedAt
    ) {
    }

    private record PersistedJob(
        long id,
        String jobType,
        String status,
        long processingRunId,
        String idempotencyKey,
        int attemptCount,
        int maxAttempts,
        OffsetDateTime availableAt
    ) {
    }

    /**
     * Wrapper de teste.
     *
     * <p>O enqueue real é executado primeiro. Somente depois o teste
     * injeta uma falha. Isso permite provar que um INSERT já realizado
     * é desfeito pelo TransactionPort.</p>
     */
    private static final class FailingAfterEnqueueQueue
        implements ProcessingJobQueuePort {

        private final ProcessingJobQueuePort delegate;

        private boolean enqueueExecuted;

        private long enqueuedJobId;

        private long processingRunId;

        private FailingAfterEnqueueQueue(
            ProcessingJobQueuePort delegate
        ) {

            this.delegate =
                delegate;
        }

        @Override
        public ProcessingJob enqueue(
            ProcessingJobSubmission submission
        ) {

            ProcessingJob job =
                delegate.enqueue(
                    submission
                );

            enqueueExecuted =
                true;

            enqueuedJobId =
                job.id();

            processingRunId =
                submission.processingRunId();

            throw new ExpectedEnqueueFailure();
        }

        @Override
        public Optional<ProcessingJob> claimNext(
            String workerId,
            OffsetDateTime claimedAt
        ) {

            return delegate.claimNext(
                workerId,
                claimedAt
            );
        }

        @Override
        public ProcessingJob markSucceeded(
            long jobId,
            String workerId,
            OffsetDateTime finishedAt
        ) {

            return delegate.markSucceeded(
                jobId,
                workerId,
                finishedAt
            );
        }

        @Override
        public ProcessingJob scheduleRetry(
            long jobId,
            String workerId,
            ProcessingFailure failure,
            OffsetDateTime availableAt,
            OffsetDateTime failedAt
        ) {

            return delegate.scheduleRetry(
                jobId,
                workerId,
                failure,
                availableAt,
                failedAt
            );
        }

        @Override
        public ProcessingJob markDead(
            long jobId,
            String workerId,
            ProcessingFailure failure,
            OffsetDateTime failedAt
        ) {

            return delegate.markDead(
                jobId,
                workerId,
                failure,
                failedAt
            );
        }

        private boolean enqueueExecuted() {

            return enqueueExecuted;
        }

        private long enqueuedJobId() {

            return enqueuedJobId;
        }

        private long processingRunId() {

            return processingRunId;
        }
    }

    private static final class ExpectedEnqueueFailure
        extends RuntimeException {

        private ExpectedEnqueueFailure() {

            super(
                "Expected failure after real ProcessingJob enqueue"
            );
        }
    }
}
