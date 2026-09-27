package com.raspingamazon.infrastructure.persistence.adapter;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class ProcessingScheduleOperationalControlJdbcTest {

    private static final URI SOURCE =
        URI.create(
            "https://www.amazon.com.br/deals"
        );

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-27T18:30:00Z"
        );

    private static final Duration INTERVAL =
        Duration.ofMinutes(
            15
        );

    private static final Duration LEASE_DURATION =
        Duration.ofMinutes(
            2
        );

    @Test
    void shouldPauseWithoutCancellingActiveLease()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            newScheduleKey(
                "pause-active"
            );

        try {

            createSchedule(
                config,
                scheduleKey,
                true
            );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter adapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                ProcessingScheduleLease lease =
                    adapter.tryAcquireDue(
                        scheduleKey,
                        "scheduler-a",
                        NOW,
                        LEASE_DURATION
                    ).orElseThrow();

                ProcessingSchedule paused =
                    adapter.pause(
                        scheduleKey,
                        NOW.plusSeconds(
                            10
                        )
                    );

                assertFalse(
                    paused.enabled()
                );

                assertTrue(
                    paused.leased()
                );

                assertEquals(
                    lease.leaseOwner(),
                    paused.leaseOwner()
                );

                assertSameInstant(
                    lease.leaseExpiresAt(),
                    paused.leaseExpiresAt()
                );

                /*
                 * Pausa não torna a janela disponível para outra
                 * instância.
                 */
                assertTrue(
                    adapter.tryAcquireDue(
                        scheduleKey,
                        "scheduler-b",
                        NOW.plusSeconds(
                            20
                        ),
                        LEASE_DURATION
                    ).isEmpty()
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
    void shouldRejectResumeWhileLeaseIsStillActive()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            newScheduleKey(
                "resume-active"
            );

        try {

            createSchedule(
                config,
                scheduleKey,
                true
            );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                JdbcProcessingScheduleAdapter adapter =
                    new JdbcProcessingScheduleAdapter(
                        connection
                    );

                ProcessingScheduleLease lease =
                    adapter.tryAcquireDue(
                        scheduleKey,
                        "scheduler-a",
                        NOW,
                        LEASE_DURATION
                    ).orElseThrow();

                adapter.pause(
                    scheduleKey,
                    NOW.plusSeconds(
                        5
                    )
                );

                OffsetDateTime attemptedAt =
                    NOW.plusSeconds(
                        30
                    );

                IllegalStateException failure =
                    assertThrows(
                        IllegalStateException.class,
                        () -> adapter.resume(
                            scheduleKey,
                            attemptedAt,
                            attemptedAt
                        )
                    );

                assertEquals(
                    "ProcessingSchedule cannot be resumed "
                        + "while an active lease exists",
                    failure.getMessage()
                );

                ProcessingSchedule persisted =
                    adapter.findByKey(
                        scheduleKey
                    ).orElseThrow();

                assertFalse(
                    persisted.enabled()
                );

                assertTrue(
                    persisted.leased()
                );

                assertEquals(
                    lease.leaseOwner(),
                    persisted.leaseOwner()
                );

                assertSameInstant(
                    NOW,
                    persisted.nextRunAt()
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
    void shouldRejectIntervalChangeWhileLeaseIsStillActive()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            newScheduleKey(
                "interval-active"
            );

        try {

            createSchedule(
                config,
                scheduleKey,
                true
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
                    "scheduler-a",
                    NOW,
                    LEASE_DURATION
                ).orElseThrow();

                Duration newInterval =
                    Duration.ofMinutes(
                        30
                    );

                OffsetDateTime changedAt =
                    NOW.plusSeconds(
                        30
                    );

                assertThrows(
                    IllegalStateException.class,
                    () -> adapter.changeInterval(
                        scheduleKey,
                        newInterval,
                        changedAt.plus(
                            newInterval
                        ),
                        changedAt
                    )
                );

                ProcessingSchedule persisted =
                    adapter.findByKey(
                        scheduleKey
                    ).orElseThrow();

                assertEquals(
                    INTERVAL,
                    persisted.interval()
                );

                assertTrue(
                    persisted.leased()
                );

                assertSameInstant(
                    NOW,
                    persisted.nextRunAt()
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
    void shouldResumeAfterLeaseExpirationAndClearStaleLease()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            newScheduleKey(
                "resume-expired"
            );

        try {

            createSchedule(
                config,
                scheduleKey,
                true
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
                    "scheduler-a",
                    NOW,
                    Duration.ofSeconds(
                        30
                    )
                ).orElseThrow();

                adapter.pause(
                    scheduleKey,
                    NOW.plusSeconds(
                        5
                    )
                );

                OffsetDateTime resumedAt =
                    NOW.plusMinutes(
                        1
                    );

                ProcessingSchedule resumed =
                    adapter.resume(
                        scheduleKey,
                        resumedAt,
                        resumedAt
                    );

                assertTrue(
                    resumed.enabled()
                );

                assertFalse(
                    resumed.leased()
                );

                assertNull(
                    resumed.leaseOwner()
                );

                assertNull(
                    resumed.leaseExpiresAt()
                );

                assertSameInstant(
                    resumedAt,
                    resumed.nextRunAt()
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
    void shouldChangeIntervalAfterLeaseExpirationAndClearStaleLease()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        String scheduleKey =
            newScheduleKey(
                "interval-expired"
            );

        try {

            createSchedule(
                config,
                scheduleKey,
                true
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
                    "scheduler-a",
                    NOW,
                    Duration.ofSeconds(
                        30
                    )
                ).orElseThrow();

                OffsetDateTime changedAt =
                    NOW.plusMinutes(
                        1
                    );

                Duration newInterval =
                    Duration.ofMinutes(
                        45
                    );

                OffsetDateTime nextRunAt =
                    changedAt.plus(
                        newInterval
                    );

                ProcessingSchedule changed =
                    adapter.changeInterval(
                        scheduleKey,
                        newInterval,
                        nextRunAt,
                        changedAt
                    );

                assertEquals(
                    newInterval,
                    changed.interval()
                );

                assertFalse(
                    changed.leased()
                );

                assertNull(
                    changed.leaseOwner()
                );

                assertNull(
                    changed.leaseExpiresAt()
                );

                assertSameInstant(
                    nextRunAt,
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

    private void createSchedule(
        ApplicationConfig config,
        String scheduleKey,
        boolean enabled
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
                    enabled,
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

    private String newScheduleKey(
        String purpose
    ) {

        return "fase17-control-"
            + purpose
            + "-"
            + UUID.randomUUID();
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
                 );
             PreparedStatement statement =
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
    }
}
