package com.raspingamazon.application.scheduling;

import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingScheduleOperationalUseCasesTest {

    private static final String SCHEDULE_KEY =
        "amazon-deals";

    private static final URI SOURCE =
        URI.create(
            "https://www.amazon.com.br/deals"
        );

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-27T18:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-27T18:00:00Z"
            ),
            ZoneOffset.UTC
        );

    private static final Duration INTERVAL =
        Duration.ofMinutes(
            15
        );

    @Test
    void shouldGetPersistedScheduleStatus() {

        FakeSchedulePort port =
            new FakeSchedulePort(
                schedule(
                    true,
                    INTERVAL,
                    NOW.plusMinutes(
                        10
                    )
                )
            );

        GetProcessingScheduleUseCase useCase =
            new GetProcessingScheduleUseCase(
                port
            );

        ProcessingSchedule result =
            useCase.execute(
                SCHEDULE_KEY
            ).orElseThrow();

        assertEquals(
            SCHEDULE_KEY,
            result.scheduleKey()
        );

        assertTrue(
            result.enabled()
        );
    }

    @Test
    void shouldReturnEmptyWhenScheduleDoesNotExist() {

        FakeSchedulePort port =
            new FakeSchedulePort(
                null
            );

        GetProcessingScheduleUseCase useCase =
            new GetProcessingScheduleUseCase(
                port
            );

        assertTrue(
            useCase.execute(
                SCHEDULE_KEY
            ).isEmpty()
        );
    }

    @Test
    void shouldPauseScheduleAtCurrentTime() {

        FakeSchedulePort port =
            new FakeSchedulePort(
                schedule(
                    true,
                    INTERVAL,
                    NOW.plusMinutes(
                        10
                    )
                )
            );

        PauseProcessingScheduleUseCase useCase =
            new PauseProcessingScheduleUseCase(
                port,
                CLOCK
            );

        ProcessingSchedule result =
            useCase.execute(
                SCHEDULE_KEY
            );

        assertFalse(
            result.enabled()
        );

        assertEquals(
            NOW,
            port.lastChangedAt
        );
    }

    @Test
    void shouldResumeScheduleImmediately() {

        FakeSchedulePort port =
            new FakeSchedulePort(
                schedule(
                    false,
                    INTERVAL,
                    NOW.plusHours(
                        2
                    )
                )
            );

        ResumeProcessingScheduleUseCase useCase =
            new ResumeProcessingScheduleUseCase(
                port,
                CLOCK
            );

        ProcessingSchedule result =
            useCase.execute(
                SCHEDULE_KEY
            );

        assertTrue(
            result.enabled()
        );

        assertEquals(
            NOW,
            port.lastNextRunAt
        );

        assertEquals(
            NOW,
            port.lastChangedAt
        );
    }

    @Test
    void shouldChangeIntervalFromCurrentTime() {

        FakeSchedulePort port =
            new FakeSchedulePort(
                schedule(
                    true,
                    INTERVAL,
                    NOW.plusMinutes(
                        5
                    )
                )
            );

        ChangeProcessingScheduleIntervalUseCase useCase =
            new ChangeProcessingScheduleIntervalUseCase(
                port,
                CLOCK
            );

        Duration newInterval =
            Duration.ofMinutes(
                30
            );

        ProcessingSchedule result =
            useCase.execute(
                SCHEDULE_KEY,
                newInterval
            );

        assertEquals(
            newInterval,
            result.interval()
        );

        assertEquals(
            NOW.plus(
                newInterval
            ),
            result.nextRunAt()
        );

        assertEquals(
            newInterval,
            port.lastInterval
        );

        assertEquals(
            NOW.plus(
                newInterval
            ),
            port.lastNextRunAt
        );
    }

    @Test
    void shouldRejectNonPositiveInterval() {

        FakeSchedulePort port =
            new FakeSchedulePort(
                schedule(
                    true,
                    INTERVAL,
                    NOW
                )
            );

        ChangeProcessingScheduleIntervalUseCase useCase =
            new ChangeProcessingScheduleIntervalUseCase(
                port,
                CLOCK
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                SCHEDULE_KEY,
                Duration.ZERO
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                SCHEDULE_KEY,
                Duration.ofMinutes(
                    -1
                )
            )
        );
    }

    @Test
    void shouldReportMissingScheduleForMutationUseCases() {

        FakeSchedulePort port =
            new FakeSchedulePort(
                null
            );

        assertThrows(
            ProcessingScheduleNotFoundException.class,
            () -> new PauseProcessingScheduleUseCase(
                port,
                CLOCK
            ).execute(
                SCHEDULE_KEY
            )
        );

        assertThrows(
            ProcessingScheduleNotFoundException.class,
            () -> new ResumeProcessingScheduleUseCase(
                port,
                CLOCK
            ).execute(
                SCHEDULE_KEY
            )
        );

        assertThrows(
            ProcessingScheduleNotFoundException.class,
            () -> new ChangeProcessingScheduleIntervalUseCase(
                port,
                CLOCK
            ).execute(
                SCHEDULE_KEY,
                Duration.ofMinutes(
                    30
                )
            )
        );
    }

    @Test
    void shouldRejectBlankScheduleKey() {

        FakeSchedulePort port =
            new FakeSchedulePort(
                schedule(
                    true,
                    INTERVAL,
                    NOW
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new GetProcessingScheduleUseCase(
                port
            ).execute(
                " "
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new PauseProcessingScheduleUseCase(
                port,
                CLOCK
            ).execute(
                " "
            )
        );
    }

    private ProcessingSchedule schedule(
        boolean enabled,
        Duration interval,
        OffsetDateTime nextRunAt
    ) {

        return new ProcessingSchedule(
            SCHEDULE_KEY,
            SOURCE,
            enabled,
            interval,
            nextRunAt,
            null,
            null,
            null,
            null,
            NOW.minusDays(
                1
            ),
            NOW.minusDays(
                1
            )
        );
    }

    private static final class FakeSchedulePort
        implements ProcessingSchedulePort {

        private ProcessingSchedule schedule;

        private Duration lastInterval;

        private OffsetDateTime lastNextRunAt;

        private OffsetDateTime lastChangedAt;

        private FakeSchedulePort(
            ProcessingSchedule schedule
        ) {

            this.schedule =
                schedule;
        }

        @Override
        public ProcessingSchedule saveIfAbsent(
            ProcessingSchedule schedule
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProcessingSchedule> findByKey(
            String scheduleKey
        ) {

            return Optional.ofNullable(
                schedule
            );
        }

        @Override
        public Optional<ProcessingScheduleLease> tryAcquireDue(
            String scheduleKey,
            String leaseOwner,
            OffsetDateTime now,
            Duration leaseDuration
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingSchedule confirmScheduled(
            String scheduleKey,
            String leaseOwner,
            OffsetDateTime scheduledFor,
            long processingRunId,
            OffsetDateTime nextRunAt,
            OffsetDateTime confirmedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingSchedule releaseLease(
            String scheduleKey,
            String leaseOwner,
            OffsetDateTime releasedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingSchedule pause(
            String scheduleKey,
            OffsetDateTime changedAt
        ) {

            lastChangedAt =
                changedAt;

            schedule =
                new ProcessingSchedule(
                    schedule.scheduleKey(),
                    schedule.source(),
                    false,
                    schedule.interval(),
                    schedule.nextRunAt(),
                    schedule.leaseOwner(),
                    schedule.leaseExpiresAt(),
                    schedule.lastScheduledFor(),
                    schedule.lastProcessingRunId(),
                    schedule.createdAt(),
                    changedAt
                );

            return schedule;
        }

        @Override
        public ProcessingSchedule resume(
            String scheduleKey,
            OffsetDateTime nextRunAt,
            OffsetDateTime changedAt
        ) {

            lastNextRunAt =
                nextRunAt;

            lastChangedAt =
                changedAt;

            schedule =
                new ProcessingSchedule(
                    schedule.scheduleKey(),
                    schedule.source(),
                    true,
                    schedule.interval(),
                    nextRunAt,
                    null,
                    null,
                    schedule.lastScheduledFor(),
                    schedule.lastProcessingRunId(),
                    schedule.createdAt(),
                    changedAt
                );

            return schedule;
        }

        @Override
        public ProcessingSchedule changeInterval(
            String scheduleKey,
            Duration interval,
            OffsetDateTime nextRunAt,
            OffsetDateTime changedAt
        ) {

            lastInterval =
                interval;

            lastNextRunAt =
                nextRunAt;

            lastChangedAt =
                changedAt;

            schedule =
                new ProcessingSchedule(
                    schedule.scheduleKey(),
                    schedule.source(),
                    schedule.enabled(),
                    interval,
                    nextRunAt,
                    null,
                    null,
                    schedule.lastScheduledFor(),
                    schedule.lastProcessingRunId(),
                    schedule.createdAt(),
                    changedAt
                );

            return schedule;
        }
    }
}
