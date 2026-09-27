package com.raspingamazon.application.scheduling;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingScheduleContractsTest {

    private static final URI SOURCE =
        URI.create(
            "https://www.amazon.com.br/deals"
        );

    private static final Duration INTERVAL =
        Duration.ofMinutes(
            15
        );

    private static final OffsetDateTime CREATED_AT =
        OffsetDateTime.parse(
            "2026-09-27T12:00:00-03:00"
        );

    private static final OffsetDateTime NEXT_RUN_AT =
        OffsetDateTime.parse(
            "2026-09-27T12:15:00-03:00"
        );

    private static final OffsetDateTime LEASE_EXPIRES_AT =
        OffsetDateTime.parse(
            "2026-09-27T12:16:00-03:00"
        );

    @Test
    void shouldRepresentEnabledScheduleWithoutLease() {

        ProcessingSchedule schedule =
            new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                true,
                INTERVAL,
                NEXT_RUN_AT,
                null,
                null,
                null,
                null,
                CREATED_AT,
                CREATED_AT
            );

        assertEquals(
            "amazon-deals",
            schedule.scheduleKey()
        );

        assertEquals(
            SOURCE,
            schedule.source()
        );

        assertEquals(
            INTERVAL,
            schedule.interval()
        );

        assertFalse(
            schedule.paused()
        );

        assertFalse(
            schedule.leased()
        );

        assertFalse(
            schedule.hasLastExecution()
        );
    }

    @Test
    void shouldRepresentPausedSchedule() {

        ProcessingSchedule schedule =
            new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                false,
                INTERVAL,
                NEXT_RUN_AT,
                null,
                null,
                null,
                null,
                CREATED_AT,
                CREATED_AT
            );

        assertTrue(
            schedule.paused()
        );
    }

    @Test
    void shouldRepresentScheduleWithLease() {

        ProcessingSchedule schedule =
            new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                true,
                INTERVAL,
                NEXT_RUN_AT,
                "instance-1",
                LEASE_EXPIRES_AT,
                null,
                null,
                CREATED_AT,
                CREATED_AT
            );

        assertTrue(
            schedule.leased()
        );

        assertEquals(
            "instance-1",
            schedule.leaseOwner()
        );
    }

    @Test
    void shouldRepresentScheduleWithLastExecution() {

        ProcessingSchedule schedule =
            new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                true,
                INTERVAL,
                NEXT_RUN_AT,
                null,
                null,
                CREATED_AT,
                100L,
                CREATED_AT,
                CREATED_AT
            );

        assertTrue(
            schedule.hasLastExecution()
        );

        assertEquals(
            100L,
            schedule.lastProcessingRunId()
        );
    }

    @Test
    void shouldRejectNonAbsoluteSource() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingSchedule(
                "amazon-deals",
                URI.create(
                    "/deals"
                ),
                true,
                INTERVAL,
                NEXT_RUN_AT,
                null,
                null,
                null,
                null,
                CREATED_AT,
                CREATED_AT
            )
        );
    }

    @Test
    void shouldRejectZeroInterval() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                true,
                Duration.ZERO,
                NEXT_RUN_AT,
                null,
                null,
                null,
                null,
                CREATED_AT,
                CREATED_AT
            )
        );
    }

    @Test
    void shouldRejectNegativeInterval() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                true,
                Duration.ofMinutes(
                    -1
                ),
                NEXT_RUN_AT,
                null,
                null,
                null,
                null,
                CREATED_AT,
                CREATED_AT
            )
        );
    }

    @Test
    void shouldRejectLeaseOwnerWithoutExpiration() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                true,
                INTERVAL,
                NEXT_RUN_AT,
                "instance-1",
                null,
                null,
                null,
                CREATED_AT,
                CREATED_AT
            )
        );
    }

    @Test
    void shouldRejectLeaseExpirationWithoutOwner() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                true,
                INTERVAL,
                NEXT_RUN_AT,
                null,
                LEASE_EXPIRES_AT,
                null,
                null,
                CREATED_AT,
                CREATED_AT
            )
        );
    }

    @Test
    void shouldRejectLastRunWithoutScheduledWindow() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                true,
                INTERVAL,
                NEXT_RUN_AT,
                null,
                null,
                null,
                100L,
                CREATED_AT,
                CREATED_AT
            )
        );
    }

    @Test
    void shouldRejectScheduledWindowWithoutLastRun() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                true,
                INTERVAL,
                NEXT_RUN_AT,
                null,
                null,
                CREATED_AT,
                null,
                CREATED_AT,
                CREATED_AT
            )
        );
    }

    @Test
    void shouldRejectNonPositiveLastRunId() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                true,
                INTERVAL,
                NEXT_RUN_AT,
                null,
                null,
                CREATED_AT,
                0L,
                CREATED_AT,
                CREATED_AT
            )
        );
    }

    @Test
    void shouldRejectUpdatedAtBeforeCreatedAt() {

        OffsetDateTime earlier =
            CREATED_AT.minusSeconds(
                1
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingSchedule(
                "amazon-deals",
                SOURCE,
                true,
                INTERVAL,
                NEXT_RUN_AT,
                null,
                null,
                null,
                null,
                CREATED_AT,
                earlier
            )
        );
    }

    @Test
    void shouldRepresentAcquiredLease() {

        ProcessingScheduleLease lease =
            new ProcessingScheduleLease(
                "amazon-deals",
                SOURCE,
                INTERVAL,
                NEXT_RUN_AT,
                "instance-1",
                LEASE_EXPIRES_AT
            );

        assertEquals(
            "amazon-deals",
            lease.scheduleKey()
        );

        assertEquals(
            NEXT_RUN_AT,
            lease.scheduledFor()
        );

        assertEquals(
            "instance-1",
            lease.leaseOwner()
        );

        assertEquals(
            LEASE_EXPIRES_AT,
            lease.leaseExpiresAt()
        );
    }

    @Test
    void shouldRejectLeaseWithBlankOwner() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingScheduleLease(
                "amazon-deals",
                SOURCE,
                INTERVAL,
                NEXT_RUN_AT,
                " ",
                LEASE_EXPIRES_AT
            )
        );
    }
}
