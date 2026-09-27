package com.raspingamazon.infrastructure.runtime;

import com.raspingamazon.application.scheduling.ScheduledProcessingRun;
import com.raspingamazon.application.scheduling.ScheduledProcessingTrigger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousProcessingSchedulerRunnerTest {

    private static final String SCHEDULE_KEY =
        "amazon-deals";

    private static final String INSTANCE_ID =
        "scheduler-instance-test";

    private static final Duration POLL_INTERVAL =
        Duration.ofSeconds(
            5
        );

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-27T18:00:00Z"
        );

    @Test
    void shouldPollAndWaitWhenNoWindowIsDue() {

        AtomicInteger triggerCalls =
            new AtomicInteger();

        ScheduledProcessingTrigger trigger =
            (scheduleKey, instanceId) -> {

                assertEquals(
                    SCHEDULE_KEY,
                    scheduleKey
                );

                assertEquals(
                    INSTANCE_ID,
                    instanceId
                );

                triggerCalls.incrementAndGet();

                return Optional.empty();
            };

        AtomicBoolean keepRunning =
            new AtomicBoolean(
                true
            );

        RecordingWaitStrategy waitStrategy =
            new RecordingWaitStrategy(
                () -> keepRunning.set(
                    false
                )
            );

        ContinuousProcessingSchedulerRunner runner =
            new ContinuousProcessingSchedulerRunner(
                trigger,
                SCHEDULE_KEY,
                INSTANCE_ID,
                POLL_INTERVAL,
                waitStrategy
            );

        runner.run(
            keepRunning::get
        );

        assertEquals(
            1,
            triggerCalls.get()
        );

        assertEquals(
            1,
            waitStrategy.calls()
        );

        assertEquals(
            POLL_INTERVAL,
            waitStrategy.lastDuration()
        );
    }

    @Test
    void shouldWaitAfterSuccessfullySchedulingRun() {

        AtomicInteger triggerCalls =
            new AtomicInteger();

        ScheduledProcessingTrigger trigger =
            (scheduleKey, instanceId) -> {

                triggerCalls.incrementAndGet();

                return Optional.of(
                    new ScheduledProcessingRun(
                        scheduleKey,
                        NOW,
                        101L,
                        201L,
                        NOW.plusMinutes(
                            15
                        )
                    )
                );
            };

        AtomicBoolean keepRunning =
            new AtomicBoolean(
                true
            );

        RecordingWaitStrategy waitStrategy =
            new RecordingWaitStrategy(
                () -> keepRunning.set(
                    false
                )
            );

        ContinuousProcessingSchedulerRunner runner =
            new ContinuousProcessingSchedulerRunner(
                trigger,
                SCHEDULE_KEY,
                INSTANCE_ID,
                POLL_INTERVAL,
                waitStrategy
            );

        runner.run(
            keepRunning::get
        );

        assertEquals(
            1,
            triggerCalls.get()
        );

        /*
         * Mesmo depois de criar uma ProcessingRun o scheduler aguarda
         * antes de consultar novamente.
         */
        assertEquals(
            1,
            waitStrategy.calls()
        );
    }

    @Test
    void shouldContinuePollingAcrossIdleCycles() {

        AtomicInteger triggerCalls =
            new AtomicInteger();

        ScheduledProcessingTrigger trigger =
            (scheduleKey, instanceId) -> {

                triggerCalls.incrementAndGet();

                return Optional.empty();
            };

        AtomicBoolean keepRunning =
            new AtomicBoolean(
                true
            );

        AtomicInteger waits =
            new AtomicInteger();

        ProcessingSchedulerWaitStrategy waitStrategy =
            duration -> {

                assertEquals(
                    POLL_INTERVAL,
                    duration
                );

                if (waits.incrementAndGet()
                    == 2) {

                    keepRunning.set(
                        false
                    );
                }
            };

        ContinuousProcessingSchedulerRunner runner =
            new ContinuousProcessingSchedulerRunner(
                trigger,
                SCHEDULE_KEY,
                INSTANCE_ID,
                POLL_INTERVAL,
                waitStrategy
            );

        runner.run(
            keepRunning::get
        );

        assertEquals(
            2,
            triggerCalls.get()
        );

        assertEquals(
            2,
            waits.get()
        );
    }

    @Test
    void shouldStopWithoutSleepingWhenShutdownIsRequestedDuringTrigger() {

        AtomicBoolean keepRunning =
            new AtomicBoolean(
                true
            );

        AtomicInteger triggerCalls =
            new AtomicInteger();

        ScheduledProcessingTrigger trigger =
            (scheduleKey, instanceId) -> {

                triggerCalls.incrementAndGet();

                keepRunning.set(
                    false
                );

                return Optional.empty();
            };

        RecordingWaitStrategy waitStrategy =
            new RecordingWaitStrategy(
                () -> {
                }
            );

        ContinuousProcessingSchedulerRunner runner =
            new ContinuousProcessingSchedulerRunner(
                trigger,
                SCHEDULE_KEY,
                INSTANCE_ID,
                POLL_INTERVAL,
                waitStrategy
            );

        runner.run(
            keepRunning::get
        );

        assertEquals(
            1,
            triggerCalls.get()
        );

        assertEquals(
            0,
            waitStrategy.calls()
        );
    }

    @Test
    void shouldStopAndRestoreInterruptFlagWhenWaitIsInterrupted() {

        AtomicInteger triggerCalls =
            new AtomicInteger();

        ScheduledProcessingTrigger trigger =
            (scheduleKey, instanceId) -> {

                triggerCalls.incrementAndGet();

                return Optional.empty();
            };

        ProcessingSchedulerWaitStrategy interruptedWait =
            duration -> {
                throw new InterruptedException(
                    "expected scheduler interruption"
                );
            };

        ContinuousProcessingSchedulerRunner runner =
            new ContinuousProcessingSchedulerRunner(
                trigger,
                SCHEDULE_KEY,
                INSTANCE_ID,
                POLL_INTERVAL,
                interruptedWait
            );

        try {

            runner.run(
                () -> true
            );

            assertTrue(
                Thread.currentThread()
                    .isInterrupted()
            );

            assertEquals(
                1,
                triggerCalls.get()
            );

        } finally {

            Thread.interrupted();
        }

        assertFalse(
            Thread.currentThread()
                .isInterrupted()
        );
    }

    @Test
    void shouldPropagateUnexpectedTriggerFailure() {

        ScheduledProcessingTrigger trigger =
            (scheduleKey, instanceId) -> {

                throw new IllegalStateException(
                    "expected scheduler failure"
                );
            };

        RecordingWaitStrategy waitStrategy =
            new RecordingWaitStrategy(
                () -> {
                }
            );

        ContinuousProcessingSchedulerRunner runner =
            new ContinuousProcessingSchedulerRunner(
                trigger,
                SCHEDULE_KEY,
                INSTANCE_ID,
                POLL_INTERVAL,
                waitStrategy
            );

        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () -> runner.run(
                    () -> true
                )
            );

        assertEquals(
            "expected scheduler failure",
            failure.getMessage()
        );

        /*
         * Falha inesperada não vira polling silencioso.
         */
        assertEquals(
            0,
            waitStrategy.calls()
        );
    }

    @Test
    void shouldRejectInvalidRuntimeConfiguration() {

        ScheduledProcessingTrigger trigger =
            (scheduleKey, instanceId) ->
                Optional.empty();

        RecordingWaitStrategy waitStrategy =
            new RecordingWaitStrategy(
                () -> {
                }
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ContinuousProcessingSchedulerRunner(
                trigger,
                " ",
                INSTANCE_ID,
                POLL_INTERVAL,
                waitStrategy
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ContinuousProcessingSchedulerRunner(
                trigger,
                SCHEDULE_KEY,
                " ",
                POLL_INTERVAL,
                waitStrategy
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ContinuousProcessingSchedulerRunner(
                trigger,
                SCHEDULE_KEY,
                INSTANCE_ID,
                Duration.ZERO,
                waitStrategy
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ContinuousProcessingSchedulerRunner(
                trigger,
                SCHEDULE_KEY,
                INSTANCE_ID,
                Duration.ofSeconds(
                    -1
                ),
                waitStrategy
            )
        );
    }

    @Test
    void shouldNotInvokeTriggerWhenStoppedBeforeStart() {

        AtomicInteger triggerCalls =
            new AtomicInteger();

        ScheduledProcessingTrigger trigger =
            (scheduleKey, instanceId) -> {

                triggerCalls.incrementAndGet();

                return Optional.empty();
            };

        RecordingWaitStrategy waitStrategy =
            new RecordingWaitStrategy(
                () -> {
                }
            );

        ContinuousProcessingSchedulerRunner runner =
            new ContinuousProcessingSchedulerRunner(
                trigger,
                SCHEDULE_KEY,
                INSTANCE_ID,
                POLL_INTERVAL,
                waitStrategy
            );

        runner.run(
            () -> false
        );

        assertEquals(
            0,
            triggerCalls.get()
        );

        assertEquals(
            0,
            waitStrategy.calls()
        );
    }

    private static final class RecordingWaitStrategy
        implements ProcessingSchedulerWaitStrategy {

        private final Runnable afterWait;

        private int calls;

        private Duration lastDuration;

        private RecordingWaitStrategy(
            Runnable afterWait
        ) {

            this.afterWait =
                afterWait;
        }

        @Override
        public void await(
            Duration duration
        ) {

            calls++;

            lastDuration =
                duration;

            afterWait.run();
        }

        private int calls() {

            return calls;
        }

        private Duration lastDuration() {

            return lastDuration;
        }
    }
}
