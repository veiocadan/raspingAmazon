package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.ProcessingRun;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.scheduling.ProcessingSchedule;
import com.raspingamazon.application.scheduling.ProcessingScheduleLease;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova a persistência e a coordenação concorrente do scheduler
 * contra PostgreSQL real.
 */
@PostgresIntegrationTest
class JdbcProcessingScheduleAdapterTest {

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

    private static final OffsetDateTime CREATED_AT =
        OffsetDateTime.parse(
            "2026-09-27T12:00:00-03:00"
        );

    private static final OffsetDateTime DUE_AT =
        OffsetDateTime.parse(
            "2026-09-27T12:15:00-03:00"
        );

    @Test
    void shouldSaveAndFindScheduleIdempotently()
        throws Exception {

        String scheduleKey =
            newScheduleKey(
                "save"
            );

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try {

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter adapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                ProcessingSchedule first =
                    adapter.saveIfAbsent(
                        createSchedule(
                            scheduleKey,
                            true,
                            INTERVAL,
                            DUE_AT
                        )
                    );

                ProcessingSchedule second =
                    adapter.saveIfAbsent(
                        createSchedule(
                            scheduleKey,
                            false,
                            Duration.ofMinutes(
                                30
                            ),
                            DUE_AT.plusHours(
                                1
                            )
                        )
                    );

                assertEquals(
                    first.scheduleKey(),
                    second.scheduleKey()
                );

                assertTrue(
                    second.enabled()
                );

                assertEquals(
                    INTERVAL,
                    second.interval()
                );

                assertSameInstant(
                    DUE_AT,
                    second.nextRunAt()
                );

                ProcessingSchedule loaded =
                    adapter.findByKey(
                        scheduleKey
                    ).orElseThrow();

                assertEquals(
                    scheduleKey,
                    loaded.scheduleKey()
                );

                assertEquals(
                    SOURCE,
                    loaded.source()
                );
            }

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    @Test
    void shouldAcquireDueSchedule()
        throws Exception {

        String scheduleKey =
            newScheduleKey(
                "acquire"
            );

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try {

            saveInitialSchedule(
                config,
                scheduleKey,
                true,
                DUE_AT
            );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter adapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                Optional<ProcessingScheduleLease> acquired =
                    adapter.tryAcquireDue(
                        scheduleKey,
                        "instance-a",
                        DUE_AT,
                        LEASE_DURATION
                    );

                assertTrue(
                    acquired.isPresent()
                );

                ProcessingScheduleLease lease =
                    acquired.orElseThrow();

                assertEquals(
                    "instance-a",
                    lease.leaseOwner()
                );

                assertSameInstant(
                    DUE_AT,
                    lease.scheduledFor()
                );

                ProcessingSchedule persisted =
                    adapter.findByKey(
                        scheduleKey
                    ).orElseThrow();

                assertTrue(
                    persisted.leased()
                );

                assertEquals(
                    "instance-a",
                    persisted.leaseOwner()
                );
            }

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    @Test
    void shouldAllowOnlyOneConcurrentLeaseOwner()
        throws Exception {

        String scheduleKey =
            newScheduleKey(
                "concurrent"
            );

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        ExecutorService executor =
            Executors.newFixedThreadPool(
                2
            );

        try {

            saveInitialSchedule(
                config,
                scheduleKey,
                true,
                DUE_AT
            );

            CountDownLatch readyLatch =
                new CountDownLatch(
                    2
                );

            CountDownLatch startLatch =
                new CountDownLatch(
                    1
                );

            Future<Optional<ProcessingScheduleLease>>
                firstFuture =
                executor.submit(
                    () -> acquireConcurrently(
                        config,
                        scheduleKey,
                        "instance-a",
                        readyLatch,
                        startLatch
                    )
                );

            Future<Optional<ProcessingScheduleLease>>
                secondFuture =
                executor.submit(
                    () -> acquireConcurrently(
                        config,
                        scheduleKey,
                        "instance-b",
                        readyLatch,
                        startLatch
                    )
                );

            assertTrue(
                readyLatch.await(
                    5,
                    TimeUnit.SECONDS
                ),
                "Both scheduler instances should become ready"
            );

            startLatch.countDown();

            Optional<ProcessingScheduleLease> first =
                firstFuture.get(
                    10,
                    TimeUnit.SECONDS
                );

            Optional<ProcessingScheduleLease> second =
                secondFuture.get(
                    10,
                    TimeUnit.SECONDS
                );

            assertNotEquals(
                first.isPresent(),
                second.isPresent()
            );

            assertTrue(
                first.isPresent()
                    || second.isPresent()
            );

            assertFalse(
                first.isPresent()
                    && second.isPresent()
            );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter adapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                ProcessingSchedule persisted =
                    adapter.findByKey(
                        scheduleKey
                    ).orElseThrow();

                assertTrue(
                    persisted.leased()
                );

                assertTrue(
                    persisted.leaseOwner()
                        .equals(
                            "instance-a"
                        )
                        || persisted.leaseOwner()
                        .equals(
                            "instance-b"
                        )
                );
            }

        } finally {

            executor.shutdownNow();

            executor.awaitTermination(
                5,
                TimeUnit.SECONDS
            );

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    @Test
    void shouldRecoverExpiredLease()
        throws Exception {

        String scheduleKey =
            newScheduleKey(
                "expired"
            );

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try {

            saveInitialSchedule(
                config,
                scheduleKey,
                true,
                DUE_AT
            );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter adapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                Optional<ProcessingScheduleLease> first =
                    adapter.tryAcquireDue(
                        scheduleKey,
                        "instance-a",
                        DUE_AT,
                        Duration.ofMinutes(
                            1
                        )
                    );

                assertTrue(
                    first.isPresent()
                );

                OffsetDateTime afterExpiration =
                    DUE_AT.plusMinutes(
                        2
                    );

                Optional<ProcessingScheduleLease> recovered =
                    adapter.tryAcquireDue(
                        scheduleKey,
                        "instance-b",
                        afterExpiration,
                        LEASE_DURATION
                    );

                assertTrue(
                    recovered.isPresent()
                );

                assertEquals(
                    "instance-b",
                    recovered.orElseThrow()
                        .leaseOwner()
                );
            }

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    @Test
    void shouldConfirmScheduledRunAndAdvanceNextRun()
        throws Exception {

        String scheduleKey =
            newScheduleKey(
                "confirm"
            );

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try {

            saveInitialSchedule(
                config,
                scheduleKey,
                true,
                DUE_AT
            );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter scheduleAdapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                ProcessingScheduleLease lease =
                    scheduleAdapter.tryAcquireDue(
                        scheduleKey,
                        "instance-a",
                        DUE_AT,
                        LEASE_DURATION
                    ).orElseThrow();

                JdbcProcessingRunRepositoryAdapter runRepository =
                    new JdbcProcessingRunRepositoryAdapter(
                        connection
                    );

                ProcessingRun run =
                    runRepository.save(
                        new ProcessingRun(
                            null,
                            runKey(
                                scheduleKey
                            ),
                            SOURCE,
                            ProcessingRunStatus.PENDING,
                            DUE_AT,
                            null,
                            null,
                            null,
                            null
                        )
                    );

                OffsetDateTime confirmedAt =
                    DUE_AT.plusSeconds(
                        10
                    );

                OffsetDateTime nextRunAt =
                    DUE_AT.plus(
                        INTERVAL
                    );

                ProcessingSchedule confirmed =
                    scheduleAdapter.confirmScheduled(
                        scheduleKey,
                        lease.leaseOwner(),
                        lease.scheduledFor(),
                        run.id(),
                        nextRunAt,
                        confirmedAt
                    );

                assertFalse(
                    confirmed.leased()
                );

                assertTrue(
                    confirmed.hasLastExecution()
                );

                assertEquals(
                    run.id(),
                    confirmed.lastProcessingRunId()
                );

                assertSameInstant(
                    DUE_AT,
                    confirmed.lastScheduledFor()
                );

                assertSameInstant(
                    nextRunAt,
                    confirmed.nextRunAt()
                );
            }

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    @Test
    void shouldPauseAndResumeSchedule()
        throws Exception {

        String scheduleKey =
            newScheduleKey(
                "pause"
            );

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try {

            saveInitialSchedule(
                config,
                scheduleKey,
                true,
                DUE_AT
            );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter adapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                ProcessingSchedule paused =
                    adapter.pause(
                        scheduleKey,
                        DUE_AT.minusSeconds(
                            10
                        )
                    );

                assertTrue(
                    paused.paused()
                );

                Optional<ProcessingScheduleLease> whilePaused =
                    adapter.tryAcquireDue(
                        scheduleKey,
                        "instance-a",
                        DUE_AT,
                        LEASE_DURATION
                    );

                assertTrue(
                    whilePaused.isEmpty()
                );

                ProcessingSchedule resumed =
                    adapter.resume(
                        scheduleKey,
                        DUE_AT,
                        DUE_AT
                    );

                assertFalse(
                    resumed.paused()
                );

                Optional<ProcessingScheduleLease> afterResume =
                    adapter.tryAcquireDue(
                        scheduleKey,
                        "instance-a",
                        DUE_AT,
                        LEASE_DURATION
                    );

                assertTrue(
                    afterResume.isPresent()
                );
            }

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    @Test
    void shouldReleaseLeaseWithoutAdvancingWindow()
        throws Exception {

        String scheduleKey =
            newScheduleKey(
                "release"
            );

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try {

            saveInitialSchedule(
                config,
                scheduleKey,
                true,
                DUE_AT
            );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter adapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                adapter.tryAcquireDue(
                    scheduleKey,
                    "instance-a",
                    DUE_AT,
                    LEASE_DURATION
                ).orElseThrow();

                ProcessingSchedule released =
                    adapter.releaseLease(
                        scheduleKey,
                        "instance-a",
                        DUE_AT.plusSeconds(
                            10
                        )
                    );

                assertFalse(
                    released.leased()
                );

                assertSameInstant(
                    DUE_AT,
                    released.nextRunAt()
                );

                Optional<ProcessingScheduleLease> reacquired =
                    adapter.tryAcquireDue(
                        scheduleKey,
                        "instance-b",
                        DUE_AT.plusSeconds(
                            11
                        ),
                        LEASE_DURATION
                    );

                assertTrue(
                    reacquired.isPresent()
                );

                assertEquals(
                    "instance-b",
                    reacquired.orElseThrow()
                        .leaseOwner()
                );
            }

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    @Test
    void shouldChangeConfiguredInterval()
        throws Exception {

        String scheduleKey =
            newScheduleKey(
                "interval"
            );

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try {

            saveInitialSchedule(
                config,
                scheduleKey,
                true,
                DUE_AT
            );

            Duration newInterval =
                Duration.ofMinutes(
                    30
                );

            OffsetDateTime newNextRunAt =
                DUE_AT.plus(
                    newInterval
                );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter adapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                ProcessingSchedule changed =
                    adapter.changeInterval(
                        scheduleKey,
                        newInterval,
                        newNextRunAt,
                        DUE_AT.minusSeconds(
                            5
                        )
                    );

                assertEquals(
                    newInterval,
                    changed.interval()
                );

                assertSameInstant(
                    newNextRunAt,
                    changed.nextRunAt()
                );
            }

        } finally {

            cleanup(
                config,
                scheduleKey
            );
        }
    }

    private Optional<ProcessingScheduleLease>
    acquireConcurrently(
        ApplicationConfig config,
        String scheduleKey,
        String leaseOwner,
        CountDownLatch readyLatch,
        CountDownLatch startLatch
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcProcessingScheduleAdapter adapter =
                new JdbcProcessingScheduleAdapter(
                    connection
                );

            readyLatch.countDown();

            if (!startLatch.await(
                5,
                TimeUnit.SECONDS
            )) {

                throw new IllegalStateException(
                    "Timed out waiting for concurrent scheduler start"
                );
            }

            return adapter.tryAcquireDue(
                scheduleKey,
                leaseOwner,
                DUE_AT,
                LEASE_DURATION
            );
        }
    }

    private void saveInitialSchedule(
        ApplicationConfig config,
        String scheduleKey,
        boolean enabled,
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

            adapter.saveIfAbsent(
                createSchedule(
                    scheduleKey,
                    enabled,
                    INTERVAL,
                    nextRunAt
                )
            );
        }
    }

    private ProcessingSchedule createSchedule(
        String scheduleKey,
        boolean enabled,
        Duration interval,
        OffsetDateTime nextRunAt
    ) {

        return new ProcessingSchedule(
            scheduleKey,
            SOURCE,
            enabled,
            interval,
            nextRunAt,
            null,
            null,
            null,
            null,
            CREATED_AT,
            CREATED_AT
        );
    }

    private String newScheduleKey(
        String purpose
    ) {

        return "fase17-"
            + purpose
            + "-"
            + UUID.randomUUID();
    }

    private String runKey(
        String scheduleKey
    ) {

        return "scheduled:"
            + scheduleKey
            + ":"
            + DUE_AT.toInstant();
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
        String scheduleKey
    ) throws Exception {

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

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

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM processing_run
                         WHERE run_key = ?
                         """
                     )) {

                statement.setString(
                    1,
                    runKey(
                        scheduleKey
                    )
                );

                statement.executeUpdate();
            }
        }
    }
}
