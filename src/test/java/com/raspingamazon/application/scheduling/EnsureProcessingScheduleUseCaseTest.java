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

class EnsureProcessingScheduleUseCaseTest {

    private static final String SCHEDULE_KEY =
        "amazon-deals";

    private static final URI SOURCE =
        URI.create(
            "https://www.amazon.com.br/deals"
        );

    private static final Duration INITIAL_INTERVAL =
        Duration.ofMinutes(
            15
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

    @Test
    void shouldCreateImmediatelyDueSchedule() {

        RecordingSchedulePort port =
            new RecordingSchedulePort(
                null
            );

        EnsureProcessingScheduleUseCase useCase =
            new EnsureProcessingScheduleUseCase(
                port,
                CLOCK
            );

        ProcessingSchedule result =
            useCase.execute(
                SCHEDULE_KEY,
                SOURCE,
                INITIAL_INTERVAL
            );

        assertEquals(
            SCHEDULE_KEY,
            result.scheduleKey()
        );

        assertEquals(
            SOURCE,
            result.source()
        );

        assertTrue(
            result.enabled()
        );

        assertEquals(
            INITIAL_INTERVAL,
            result.interval()
        );

        assertEquals(
            NOW,
            result.nextRunAt()
        );

        assertFalse(
            result.leased()
        );

        assertFalse(
            result.hasLastExecution()
        );

        assertEquals(
            NOW,
            result.createdAt()
        );

        assertEquals(
            NOW,
            result.updatedAt()
        );
    }

    @Test
    void shouldPreserveExistingPersistedConfiguration() {

        ProcessingSchedule existing =
            new ProcessingSchedule(
                SCHEDULE_KEY,
                SOURCE,
                false,
                Duration.ofMinutes(
                    30
                ),
                NOW.plusHours(
                    2
                ),
                null,
                null,
                null,
                null,
                NOW.minusDays(
                    2
                ),
                NOW.minusHours(
                    1
                )
            );

        RecordingSchedulePort port =
            new RecordingSchedulePort(
                existing
            );

        EnsureProcessingScheduleUseCase useCase =
            new EnsureProcessingScheduleUseCase(
                port,
                CLOCK
            );

        ProcessingSchedule result =
            useCase.execute(
                SCHEDULE_KEY,
                SOURCE,
                INITIAL_INTERVAL
            );

        /*
         * O bootstrap propôs PT15M, mas a persistência já tinha
         * PT30M e estava pausada.
         *
         * O estado operacional existente vence.
         */
        assertEquals(
            existing,
            result
        );

        assertFalse(
            result.enabled()
        );

        assertEquals(
            Duration.ofMinutes(
                30
            ),
            result.interval()
        );

        assertEquals(
            NOW.plusHours(
                2
            ),
            result.nextRunAt()
        );
    }

    @Test
    void shouldRejectInvalidBootstrapConfiguration() {

        RecordingSchedulePort port =
            new RecordingSchedulePort(
                null
            );

        EnsureProcessingScheduleUseCase useCase =
            new EnsureProcessingScheduleUseCase(
                port,
                CLOCK
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                " ",
                SOURCE,
                INITIAL_INTERVAL
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                SCHEDULE_KEY,
                URI.create(
                    "/relative"
                ),
                INITIAL_INTERVAL
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                SCHEDULE_KEY,
                SOURCE,
                Duration.ZERO
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                SCHEDULE_KEY,
                SOURCE,
                Duration.ofMinutes(
                    -1
                )
            )
        );
    }

    private static final class RecordingSchedulePort
        implements ProcessingSchedulePort {

        private ProcessingSchedule persisted;

        private RecordingSchedulePort(
            ProcessingSchedule existing
        ) {

            this.persisted =
                existing;
        }

        @Override
        public ProcessingSchedule saveIfAbsent(
            ProcessingSchedule schedule
        ) {

            if (persisted == null) {

                persisted =
                    schedule;
            }

            return persisted;
        }

        @Override
        public Optional<ProcessingSchedule> findByKey(
            String scheduleKey
        ) {

            return Optional.ofNullable(
                persisted
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

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingSchedule resume(
            String scheduleKey,
            OffsetDateTime nextRunAt,
            OffsetDateTime changedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingSchedule changeInterval(
            String scheduleKey,
            Duration interval,
            OffsetDateTime nextRunAt,
            OffsetDateTime changedAt
        ) {

            throw new UnsupportedOperationException();
        }
    }
}
