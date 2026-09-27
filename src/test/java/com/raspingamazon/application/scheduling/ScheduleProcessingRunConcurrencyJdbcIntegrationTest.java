package com.raspingamazon.application.scheduling;

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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class ScheduleProcessingRunConcurrencyJdbcIntegrationTest {

    private static final URI SOURCE =
        URI.create(
            "https://www.amazon.com.br/deals"
        );

    private static final OffsetDateTime FIRST_WINDOW =
        OffsetDateTime.parse(
            "2026-09-27T20:00:00Z"
        );

    private static final Duration INTERVAL =
        Duration.ofMinutes(
            15
        );

    private static final Duration SCHEDULER_LEASE_DURATION =
        Duration.ofMinutes(
            1
        );

    private static final int COLLECTION_MAX_ATTEMPTS =
        3;

    private static final long CONCURRENCY_TIMEOUT_SECONDS =
        10L;

    @Test
    void shouldAllowOnlyOneSchedulerInstanceToCreateSameWindow()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            newScheduleKey(
                "same-window"
            );

        try {

            createSchedule(
                config,
                scheduleKey,
                FIRST_WINDOW
            );

            CompetitionResult competition =
                competeForWindow(
                    config,
                    scheduleKey,
                    FIRST_WINDOW
                );

            /*
             * Duas instÃ¢ncias tentaram a mesma janela.
             *
             * Exatamente uma deve ter conseguido adquirir o schedule
             * e criar a unidade de trabalho.
             */
            assertEquals(
                1L,
                competition.scheduledCount()
            );

            assertEquals(
                1L,
                countProcessingRuns(
                    config,
                    scheduleKey
                )
            );

            assertEquals(
                1L,
                countCollectDealsJobs(
                    config,
                    scheduleKey
                )
            );

            assertEquals(
                1L,
                countRunByWindow(
                    config,
                    scheduleKey,
                    FIRST_WINDOW
                )
            );

            ProcessingSchedule persisted =
                loadSchedule(
                    config,
                    scheduleKey
                );

            assertFalse(
                persisted.leased()
            );

            assertSameInstant(
                FIRST_WINDOW,
                persisted.lastScheduledFor()
            );

            assertSameInstant(
                FIRST_WINDOW.plus(
                    INTERVAL
                ),
                persisted.nextRunAt()
            );

            assertNotNull(
                persisted.lastProcessingRunId()
            );

            assertTrue(
                persisted.lastProcessingRunId()
                    > 0L
            );

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    @Test
    void shouldPreventOverlapUntilPreviousCollectJobBecomesTerminal()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            newScheduleKey(
                "recurrence"
            );

        OffsetDateTime secondWindow =
            FIRST_WINDOW.plus(
                INTERVAL
            );

        try {

            createSchedule(
                config,
                scheduleKey,
                FIRST_WINDOW
            );

            CompetitionResult firstCompetition =
                competeForWindow(
                    config,
                    scheduleKey,
                    FIRST_WINDOW
                );

            assertEquals(
                1L,
                firstCompetition.scheduledCount()
            );

            ProcessingSchedule afterFirstWindow =
                loadSchedule(
                    config,
                    scheduleKey
                );

            assertNotNull(
                afterFirstWindow.lastProcessingRunId()
            );

            long firstRunId =
                afterFirstWindow.lastProcessingRunId();

            /*
             * O COLLECT_DEALS criado pelo scheduler começa PENDING.
             * Mesmo que a próxima janela já esteja vencida, nenhuma
             * segunda coleta automática pode ser criada enquanto o
             * trabalho anterior ainda aguarda processamento.
             */
            Optional<ScheduledProcessingRun> blockedWhilePending =
                executeOnce(
                    config,
                    scheduleKey,
                    "scheduler-pending-overlap",
                    secondWindow
                );

            assertTrue(
                blockedWhilePending.isEmpty()
            );

            OffsetDateTime runningAt =
                secondWindow.plusSeconds(
                    1
                );

            markCollectionCycleRunning(
                config,
                firstRunId,
                runningAt
            );

            /*
             * RUNNING também representa uma coleta anterior ativa.
             */
            Optional<ScheduledProcessingRun> blockedWhileRunning =
                executeOnce(
                    config,
                    scheduleKey,
                    "scheduler-running-overlap",
                    runningAt
                );

            assertTrue(
                blockedWhileRunning.isEmpty()
            );

            OffsetDateTime failedAt =
                secondWindow.plusSeconds(
                    2
                );

            OffsetDateTime retryAvailableAt =
                secondWindow.plusMinutes(
                    1
                );

            markCollectionCycleRetryWait(
                config,
                firstRunId,
                failedAt,
                retryAvailableAt
            );

            /*
             * ProcessingRun FAILED não significa necessariamente que o
             * trabalho terminou. Enquanto o COLLECT_DEALS estiver em
             * RETRY_WAIT, a execução anterior ainda é recuperável pela
             * política de retry da FASE 12 e deve bloquear nova coleta.
             */
            Optional<ScheduledProcessingRun> blockedWhileRetryWait =
                executeOnce(
                    config,
                    scheduleKey,
                    "scheduler-retry-wait-overlap",
                    secondWindow.plusSeconds(
                        3
                    )
                );

            assertTrue(
                blockedWhileRetryWait.isEmpty()
            );

            assertEquals(
                1L,
                countProcessingRuns(
                    config,
                    scheduleKey
                )
            );

            assertEquals(
                1L,
                countCollectDealsJobs(
                    config,
                    scheduleKey
                )
            );

            ProcessingSchedule blockedState =
                loadSchedule(
                    config,
                    scheduleKey
                );

            assertFalse(
                blockedState.leased()
            );

            assertSameInstant(
                FIRST_WINDOW,
                blockedState.lastScheduledFor()
            );

            assertSameInstant(
                secondWindow,
                blockedState.nextRunAt()
            );

            assertEquals(
                firstRunId,
                blockedState.lastProcessingRunId()
            );

            OffsetDateTime retryCompletedAt =
                retryAvailableAt.plusSeconds(
                    1
                );

            markCollectionCycleSucceededAfterRetry(
                config,
                firstRunId,
                retryAvailableAt,
                retryCompletedAt
            );

            /*
             * SUCCEEDED é terminal para o COLLECT_DEALS anterior.
             * A janela vencida pode então ser disputada e apenas uma
             * das duas instâncias concorrentes deve criar a nova run.
             */
            CompetitionResult secondCompetition =
                competeForWindow(
                    config,
                    scheduleKey,
                    retryCompletedAt
                );

            assertEquals(
                1L,
                secondCompetition.scheduledCount()
            );

            assertEquals(
                2L,
                countProcessingRuns(
                    config,
                    scheduleKey
                )
            );

            assertEquals(
                2L,
                countCollectDealsJobs(
                    config,
                    scheduleKey
                )
            );

            assertEquals(
                1L,
                countRunByWindow(
                    config,
                    scheduleKey,
                    FIRST_WINDOW
                )
            );

            assertEquals(
                1L,
                countRunByWindow(
                    config,
                    scheduleKey,
                    secondWindow
                )
            );

            ProcessingSchedule persisted =
                loadSchedule(
                    config,
                    scheduleKey
                );

            assertFalse(
                persisted.leased()
            );

            assertSameInstant(
                secondWindow,
                persisted.lastScheduledFor()
            );

            assertSameInstant(
                secondWindow.plus(
                    INTERVAL
                ),
                persisted.nextRunAt()
            );

            assertNotNull(
                persisted.lastProcessingRunId()
            );

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    @Test
    void shouldAllowAnotherSchedulerToTakeOverExpiredLeaseWithoutDuplication()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            newScheduleKey(
                "expired-lease"
            );

        Duration shortLease =
            Duration.ofSeconds(
                30
            );

        try {

            createSchedule(
                config,
                scheduleKey,
                FIRST_WINDOW
            );

            /*
             * Simula uma instÃ¢ncia que adquiriu o lease e desapareceu
             * ANTES de criar ProcessingRun/job.
             *
             * A aquisiÃ§Ã£o abaixo usa auto-commit normal da Connection,
             * portanto o lease fica persistido no PostgreSQL.
             */
            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter adapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                ProcessingScheduleLease abandonedLease =
                    adapter.tryAcquireDue(
                        scheduleKey,
                        "scheduler-abandoned",
                        FIRST_WINDOW,
                        shortLease
                    ).orElseThrow();

                assertEquals(
                    "scheduler-abandoned",
                    abandonedLease.leaseOwner()
                );
            }

            OffsetDateTime afterExpiration =
                FIRST_WINDOW.plusSeconds(
                    31
                );

            /*
             * Depois da expiraÃ§Ã£o, outra instÃ¢ncia pode adquirir a mesma
             * janela original.
             */
            Optional<ScheduledProcessingRun> recovered =
                executeOnce(
                    config,
                    scheduleKey,
                    "scheduler-recovery",
                    afterExpiration
                );

            assertTrue(
                recovered.isPresent()
            );

            /*
             * Uma terceira tentativa no mesmo instante nÃ£o pode criar
             * outra ProcessingRun.
             */
            Optional<ScheduledProcessingRun> duplicateAttempt =
                executeOnce(
                    config,
                    scheduleKey,
                    "scheduler-third",
                    afterExpiration
                );

            assertTrue(
                duplicateAttempt.isEmpty()
            );

            assertEquals(
                1L,
                countProcessingRuns(
                    config,
                    scheduleKey
                )
            );

            assertEquals(
                1L,
                countCollectDealsJobs(
                    config,
                    scheduleKey
                )
            );

            assertEquals(
                1L,
                countRunByWindow(
                    config,
                    scheduleKey,
                    FIRST_WINDOW
                )
            );

            ProcessingSchedule persisted =
                loadSchedule(
                    config,
                    scheduleKey
                );

            assertFalse(
                persisted.leased()
            );

            /*
             * scheduledFor continua sendo a janela original.
             *
             * O fato de outra instÃ¢ncia ter retomado o trabalho depois
             * nÃ£o muda a identidade temporal daquela observaÃ§Ã£o.
             */
            assertSameInstant(
                FIRST_WINDOW,
                persisted.lastScheduledFor()
            );

            /*
             * Como somente 31 segundos transcorreram e a cadÃªncia Ã© de
             * 15 minutos, a prÃ³xima janela natural ainda estÃ¡ no futuro.
             */
            assertSameInstant(
                FIRST_WINDOW.plus(
                    INTERVAL
                ),
                persisted.nextRunAt()
            );

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    @Test
    void shouldNotCreateCatchUpBurstAfterLongDowntime()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            newScheduleKey(
                "downtime"
            );

        OffsetDateTime recoveredAt =
            FIRST_WINDOW.plusHours(
                3
            );

        try {

            createSchedule(
                config,
                scheduleKey,
                FIRST_WINDOW
            );

            /*
             * O processo ficou trÃªs horas parado.
             *
             * Existem vÃ¡rias janelas teÃ³ricas de 15 minutos entre
             * FIRST_WINDOW e recoveredAt.
             *
             * O scheduler NÃƒO deve materializar todas elas em rajada.
             */
            Optional<ScheduledProcessingRun> recovered =
                executeOnce(
                    config,
                    scheduleKey,
                    "scheduler-after-downtime",
                    recoveredAt
                );

            assertTrue(
                recovered.isPresent()
            );

            /*
             * Ainda no mesmo instante, nÃ£o existe uma segunda janela
             * vencida a ser descarregada.
             */
            Optional<ScheduledProcessingRun> immediateSecondAttempt =
                executeOnce(
                    config,
                    scheduleKey,
                    "scheduler-after-downtime-2",
                    recoveredAt
                );

            assertTrue(
                immediateSecondAttempt.isEmpty()
            );

            assertEquals(
                1L,
                countProcessingRuns(
                    config,
                    scheduleKey
                )
            );

            assertEquals(
                1L,
                countCollectDealsJobs(
                    config,
                    scheduleKey
                )
            );

            ProcessingSchedule persisted =
                loadSchedule(
                    config,
                    scheduleKey
                );

            assertSameInstant(
                FIRST_WINDOW,
                persisted.lastScheduledFor()
            );

            /*
             * Como FIRST_WINDOW + INTERVAL ficou muito no passado,
             * o prÃ³ximo ciclo Ã© calculado a partir da recuperaÃ§Ã£o:
             *
             * recoveredAt + INTERVAL
             */
            assertSameInstant(
                recoveredAt.plus(
                    INTERVAL
                ),
                persisted.nextRunAt()
            );

            assertFalse(
                persisted.leased()
            );

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    private CompetitionResult competeForWindow(
        ApplicationConfig config,
        String scheduleKey,
        OffsetDateTime now
    ) throws Exception {

        ExecutorService executor =
            Executors.newFixedThreadPool(
                2
            );

        CountDownLatch ready =
            new CountDownLatch(
                2
            );

        CountDownLatch start =
            new CountDownLatch(
                1
            );

        Future<Optional<ScheduledProcessingRun>> first =
            executor.submit(
                () ->
                    executeWhenReleased(
                        config,
                        scheduleKey,
                        "scheduler-a",
                        now,
                        ready,
                        start
                    )
            );

        Future<Optional<ScheduledProcessingRun>> second =
            executor.submit(
                () ->
                    executeWhenReleased(
                        config,
                        scheduleKey,
                        "scheduler-b",
                        now,
                        ready,
                        start
                    )
            );

        try {

            boolean bothReady =
                ready.await(
                    CONCURRENCY_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

            if (!bothReady) {

                throw new IllegalStateException(
                    "Schedulers did not become ready for concurrent test"
                );
            }

            /*
             * Libera as duas instÃ¢ncias praticamente no mesmo instante.
             */
            start.countDown();

            Optional<ScheduledProcessingRun> firstResult =
                first.get(
                    CONCURRENCY_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

            Optional<ScheduledProcessingRun> secondResult =
                second.get(
                    CONCURRENCY_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

            return new CompetitionResult(
                firstResult,
                secondResult
            );

        } finally {

            /*
             * TambÃ©m libera tarefas eventualmente bloqueadas caso uma
             * assertion anterior tenha interrompido o fluxo.
             */
            start.countDown();

            executor.shutdownNow();

            executor.awaitTermination(
                CONCURRENCY_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
        }
    }

    private Optional<ScheduledProcessingRun> executeWhenReleased(
        ApplicationConfig config,
        String scheduleKey,
        String schedulerInstanceId,
        OffsetDateTime now,
        CountDownLatch ready,
        CountDownLatch start
    ) throws Exception {

        ready.countDown();

        boolean released =
            start.await(
                CONCURRENCY_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );

        if (!released) {

            throw new IllegalStateException(
                "Concurrent scheduler start was not released"
            );
        }

        return executeOnce(
            config,
            scheduleKey,
            schedulerInstanceId,
            now
        );
    }

    private Optional<ScheduledProcessingRun> executeOnce(
        ApplicationConfig config,
        String scheduleKey,
        String schedulerInstanceId,
        OffsetDateTime now
    ) throws Exception {

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

            JdbcTransactionAdapter transactionAdapter =
                new JdbcTransactionAdapter(
                    connection
                );

            Clock clock =
                Clock.fixed(
                    now.toInstant(),
                    ZoneOffset.UTC
                );

            ScheduleProcessingRunUseCase useCase =
                new ScheduleProcessingRunUseCase(
                    scheduleAdapter,
                    runRepository,
                    jobQueue,
                    transactionAdapter,
                    clock,
                    SCHEDULER_LEASE_DURATION,
                    COLLECTION_MAX_ATTEMPTS
                );

            return useCase.execute(
                scheduleKey,
                schedulerInstanceId
            );
        }
    }

    private void markCollectionCycleRunning(
        ApplicationConfig config,
        long processingRunId,
        OffsetDateTime changedAt
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            boolean originalAutoCommit =
                connection.getAutoCommit();

            connection.setAutoCommit(
                false
            );

            try {

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             UPDATE processing_run
                             SET
                                 status = 'RUNNING',
                                 started_at = ?,
                                 completed_at = NULL,
                                 last_error_code = NULL,
                                 last_error_message = NULL,
                                 updated_at = ?
                             WHERE id = ?
                               AND status = 'PENDING'
                             """
                         )) {

                    statement.setObject(
                        1,
                        changedAt
                    );

                    statement.setObject(
                        2,
                        changedAt
                    );

                    statement.setLong(
                        3,
                        processingRunId
                    );

                    assertEquals(
                        1,
                        statement.executeUpdate()
                    );
                }

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             UPDATE processing_job
                             SET
                                 status = 'RUNNING',
                                 attempt_count = 1,
                                 locked_at = ?,
                                 locked_by = 'overlap-test-worker',
                                 updated_at = ?,
                                 finished_at = NULL
                             WHERE processing_run_id = ?
                               AND job_type = 'COLLECT_DEALS'
                               AND status = 'PENDING'
                             """
                         )) {

                    statement.setObject(
                        1,
                        changedAt
                    );

                    statement.setObject(
                        2,
                        changedAt
                    );

                    statement.setLong(
                        3,
                        processingRunId
                    );

                    assertEquals(
                        1,
                        statement.executeUpdate()
                    );
                }

                connection.commit();

            } catch (Exception exception) {

                connection.rollback();

                throw exception;

            } finally {

                connection.setAutoCommit(
                    originalAutoCommit
                );
            }
        }
    }

    private void markCollectionCycleRetryWait(
        ApplicationConfig config,
        long processingRunId,
        OffsetDateTime failedAt,
        OffsetDateTime retryAvailableAt
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            boolean originalAutoCommit =
                connection.getAutoCommit();

            connection.setAutoCommit(
                false
            );

            try {

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             UPDATE processing_run
                             SET
                                 status = 'FAILED',
                                 completed_at = ?,
                                 last_error_code = 'TEST_TRANSIENT',
                                 last_error_message = 'transient test failure',
                                 updated_at = ?
                             WHERE id = ?
                               AND status = 'RUNNING'
                             """
                         )) {

                    statement.setObject(
                        1,
                        failedAt
                    );

                    statement.setObject(
                        2,
                        failedAt
                    );

                    statement.setLong(
                        3,
                        processingRunId
                    );

                    assertEquals(
                        1,
                        statement.executeUpdate()
                    );
                }

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             UPDATE processing_job
                             SET
                                 status = 'RETRY_WAIT',
                                 available_at = ?,
                                 locked_at = NULL,
                                 locked_by = NULL,
                                 last_failure_type = 'TRANSIENT',
                                 last_error_code = 'TEST_TRANSIENT',
                                 last_error_message = 'transient test failure',
                                 updated_at = ?,
                                 finished_at = NULL
                             WHERE processing_run_id = ?
                               AND job_type = 'COLLECT_DEALS'
                               AND status = 'RUNNING'
                             """
                         )) {

                    statement.setObject(
                        1,
                        retryAvailableAt
                    );

                    statement.setObject(
                        2,
                        failedAt
                    );

                    statement.setLong(
                        3,
                        processingRunId
                    );

                    assertEquals(
                        1,
                        statement.executeUpdate()
                    );
                }

                connection.commit();

            } catch (Exception exception) {

                connection.rollback();

                throw exception;

            } finally {

                connection.setAutoCommit(
                    originalAutoCommit
                );
            }
        }
    }

    private void markCollectionCycleSucceededAfterRetry(
        ApplicationConfig config,
        long processingRunId,
        OffsetDateTime retryStartedAt,
        OffsetDateTime completedAt
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            boolean originalAutoCommit =
                connection.getAutoCommit();

            connection.setAutoCommit(
                false
            );

            try {

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             UPDATE processing_run
                             SET
                                 status = 'RUNNING',
                                 started_at = ?,
                                 completed_at = NULL,
                                 last_error_code = NULL,
                                 last_error_message = NULL,
                                 updated_at = ?
                             WHERE id = ?
                               AND status = 'FAILED'
                             """
                         )) {

                    statement.setObject(
                        1,
                        retryStartedAt
                    );

                    statement.setObject(
                        2,
                        retryStartedAt
                    );

                    statement.setLong(
                        3,
                        processingRunId
                    );

                    assertEquals(
                        1,
                        statement.executeUpdate()
                    );
                }

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             UPDATE processing_job
                             SET
                                 status = 'RUNNING',
                                 attempt_count = attempt_count + 1,
                                 locked_at = ?,
                                 locked_by = 'overlap-test-worker',
                                 updated_at = ?,
                                 finished_at = NULL
                             WHERE processing_run_id = ?
                               AND job_type = 'COLLECT_DEALS'
                               AND status = 'RETRY_WAIT'
                               AND attempt_count < max_attempts
                             """
                         )) {

                    statement.setObject(
                        1,
                        retryStartedAt
                    );

                    statement.setObject(
                        2,
                        retryStartedAt
                    );

                    statement.setLong(
                        3,
                        processingRunId
                    );

                    assertEquals(
                        1,
                        statement.executeUpdate()
                    );
                }

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             UPDATE processing_run
                             SET
                                 status = 'COMPLETED',
                                 completed_at = ?,
                                 last_error_code = NULL,
                                 last_error_message = NULL,
                                 updated_at = ?
                             WHERE id = ?
                               AND status = 'RUNNING'
                             """
                         )) {

                    statement.setObject(
                        1,
                        completedAt
                    );

                    statement.setObject(
                        2,
                        completedAt
                    );

                    statement.setLong(
                        3,
                        processingRunId
                    );

                    assertEquals(
                        1,
                        statement.executeUpdate()
                    );
                }

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             UPDATE processing_job
                             SET
                                 status = 'SUCCEEDED',
                                 locked_at = NULL,
                                 locked_by = NULL,
                                 updated_at = ?,
                                 finished_at = ?
                             WHERE processing_run_id = ?
                               AND job_type = 'COLLECT_DEALS'
                               AND status = 'RUNNING'
                               AND locked_by = 'overlap-test-worker'
                             """
                         )) {

                    statement.setObject(
                        1,
                        completedAt
                    );

                    statement.setObject(
                        2,
                        completedAt
                    );

                    statement.setLong(
                        3,
                        processingRunId
                    );

                    assertEquals(
                        1,
                        statement.executeUpdate()
                    );
                }

                connection.commit();

            } catch (Exception exception) {

                connection.rollback();

                throw exception;

            } finally {

                connection.setAutoCommit(
                    originalAutoCommit
                );
            }
        }
    }

    private void createSchedule(
        ApplicationConfig config,
        String scheduleKey,
        OffsetDateTime nextRunAt
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingScheduleAdapter adapter =
                new JdbcProcessingScheduleAdapter(
                    connection
                );

            OffsetDateTime createdAt =
                nextRunAt.minusMinutes(
                    1
                );

            adapter.saveIfAbsent(
                new ProcessingSchedule(
                    scheduleKey,
                    SOURCE,
                    true,
                    INTERVAL,
                    nextRunAt,
                    null,
                    null,
                    null,
                    null,
                    createdAt,
                    createdAt
                )
            );
        }
    }

    private ProcessingSchedule loadSchedule(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            return new JdbcProcessingScheduleAdapter(
                connection
            ).findByKey(
                scheduleKey
            ).orElseThrow();
        }
    }

    private long countProcessingRuns(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM processing_run
            WHERE run_key LIKE ?
            """;

        return count(
            config,
            sql,
            scheduledRunPrefix(
                scheduleKey
            )
                + "%"
        );
    }

    private long countRunByWindow(
        ApplicationConfig config,
        String scheduleKey,
        OffsetDateTime scheduledFor
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM processing_run
            WHERE run_key = ?
            """;

        return count(
            config,
            sql,
            scheduledRunKey(
                scheduleKey,
                scheduledFor
            )
        );
    }

    private long countCollectDealsJobs(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM processing_job job
            JOIN processing_run run
              ON run.id = job.processing_run_id
            WHERE run.run_key LIKE ?
              AND job.job_type = 'COLLECT_DEALS'
            """;

        return count(
            config,
            sql,
            scheduledRunPrefix(
                scheduleKey
            )
                + "%"
        );
    }

    private long count(
        ApplicationConfig config,
        String sql,
        String value
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 );

             PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                value
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

    private String scheduledRunPrefix(
        String scheduleKey
    ) {

        return "scheduled:"
            + scheduleKey
            + ":";
    }

    private String scheduledRunKey(
        String scheduleKey,
        OffsetDateTime scheduledFor
    ) {

        return scheduledRunPrefix(
            scheduleKey
        )
            + scheduledFor
            .toInstant();
    }

    private String newScheduleKey(
        String purpose
    ) {

        return "fase17-"
            + purpose
            + "-"
            + UUID.randomUUID();
    }

    private void assertSameInstant(
        OffsetDateTime expected,
        OffsetDateTime actual
    ) {

        assertNotNull(
            actual
        );

        assertEquals(
            expected.toInstant(),
            actual.toInstant()
        );
    }

    private void cleanup(
        ApplicationConfig config,
        String scheduleKey
    ) throws Exception {

        String runKeyPattern =
            scheduledRunPrefix(
                scheduleKey
            )
                + "%";

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            boolean originalAutoCommit =
                connection.getAutoCommit();

            connection.setAutoCommit(
                false
            );

            try {

                /*
                 * processing_schedule referencia a Ãºltima ProcessingRun.
                 * Portanto removemos o schedule antes das runs.
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
                 * processing_job possui FK para processing_run.
                 */
                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM processing_job job
                             USING processing_run run
                             WHERE job.processing_run_id = run.id
                               AND run.run_key LIKE ?
                             """
                         )) {

                    statement.setString(
                        1,
                        runKeyPattern
                    );

                    statement.executeUpdate();
                }

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM processing_run
                             WHERE run_key LIKE ?
                             """
                         )) {

                    statement.setString(
                        1,
                        runKeyPattern
                    );

                    statement.executeUpdate();
                }

                connection.commit();

            } catch (Exception exception) {

                connection.rollback();

                throw exception;

            } finally {

                connection.setAutoCommit(
                    originalAutoCommit
                );
            }
        }
    }

    private record CompetitionResult(
        Optional<ScheduledProcessingRun> first,
        Optional<ScheduledProcessingRun> second
    ) {

        private long scheduledCount() {

            long count =
                0L;

            if (first.isPresent()) {
                count++;
            }

            if (second.isPresent()) {
                count++;
            }

            return count;
        }
    }
}
