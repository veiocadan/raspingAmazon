package com.raspingamazon.application.scheduling;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.ProcessingRun;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.orchestration.port.ProcessingRunRepositoryPort;
import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;
import com.raspingamazon.application.shared.port.TransactionPort;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleProcessingRunUseCaseTest {

    private static final URI SOURCE =
        URI.create(
            "https://www.amazon.com.br/deals"
        );

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-27T15:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-27T15:00:00Z"
            ),
            ZoneOffset.UTC
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
    void shouldReturnEmptyWhenNoScheduleWindowIsDue() {

        FakeSchedulePort schedulePort =
            new FakeSchedulePort(
                Optional.empty()
            );

        FakeRunRepository runRepository =
            new FakeRunRepository();

        FakeJobQueue jobQueue =
            new FakeJobQueue();

        RecordingTransactionPort transactionPort =
            new RecordingTransactionPort();

        ScheduleProcessingRunUseCase useCase =
            newUseCase(
                schedulePort,
                runRepository,
                jobQueue,
                transactionPort
            );

        Optional<ScheduledProcessingRun> result =
            useCase.execute(
                "amazon-deals",
                "scheduler-1"
            );

        assertTrue(
            result.isEmpty()
        );

        assertEquals(
            1,
            transactionPort.executions
        );

        assertEquals(
            0,
            runRepository.saveCalls
        );

        assertEquals(
            0,
            jobQueue.enqueueCalls
        );

        assertEquals(
            0,
            schedulePort.confirmCalls
        );
    }

    @Test
    void shouldCreateRunCollectJobAndConfirmSchedule() {

        ProcessingScheduleLease lease =
            leaseAt(
                NOW
            );

        FakeSchedulePort schedulePort =
            new FakeSchedulePort(
                Optional.of(
                    lease
                )
            );

        FakeRunRepository runRepository =
            new FakeRunRepository();

        FakeJobQueue jobQueue =
            new FakeJobQueue();

        RecordingTransactionPort transactionPort =
            new RecordingTransactionPort();

        ScheduleProcessingRunUseCase useCase =
            newUseCase(
                schedulePort,
                runRepository,
                jobQueue,
                transactionPort
            );

        ScheduledProcessingRun result =
            useCase.execute(
                "amazon-deals",
                "scheduler-1"
            ).orElseThrow();

        assertEquals(
            100L,
            result.processingRunId()
        );

        assertEquals(
            200L,
            result.processingJobId()
        );

        assertEquals(
            NOW.plus(
                INTERVAL
            ),
            result.nextRunAt()
        );

        assertEquals(
            "scheduled:amazon-deals:2026-09-27T15:00:00Z",
            runRepository.lastRequested.runKey()
        );

        assertEquals(
            SOURCE,
            runRepository.lastRequested.source()
        );

        assertEquals(
            ProcessingRunStatus.PENDING,
            runRepository.lastRequested.status()
        );

        assertEquals(
            NOW,
            runRepository.lastRequested.requestedAt()
        );

        assertEquals(
            ProcessingJobType.COLLECT_DEALS,
            jobQueue.lastSubmission.type()
        );

        assertEquals(
            100L,
            jobQueue.lastSubmission.processingRunId()
        );

        assertEquals(
            "collect:100",
            jobQueue.lastSubmission.idempotencyKey()
        );

        assertEquals(
            5,
            jobQueue.lastSubmission.maxAttempts()
        );

        assertEquals(
            NOW,
            jobQueue.lastSubmission.availableAt()
        );

        assertEquals(
            1,
            schedulePort.confirmCalls
        );
    }

    @Test
    void shouldSkipUnlimitedCatchUpAfterLongDowntime() {

        OffsetDateTime oldWindow =
            NOW.minusHours(
                3
            );

        FakeSchedulePort schedulePort =
            new FakeSchedulePort(
                Optional.of(
                    leaseAt(
                        oldWindow
                    )
                )
            );

        ScheduleProcessingRunUseCase useCase =
            newUseCase(
                schedulePort,
                new FakeRunRepository(),
                new FakeJobQueue(),
                new RecordingTransactionPort()
            );

        ScheduledProcessingRun result =
            useCase.execute(
                "amazon-deals",
                "scheduler-1"
            ).orElseThrow();

        assertEquals(
            NOW.plus(
                INTERVAL
            ),
            result.nextRunAt()
        );

        assertEquals(
            NOW.plus(
                INTERVAL
            ),
            schedulePort.confirmedNextRunAt
        );
    }

    @Test
    void shouldPreserveNaturalNextWindowWhenStillInFuture() {

        OffsetDateTime scheduledFor =
            NOW.minusMinutes(
                5
            );

        FakeSchedulePort schedulePort =
            new FakeSchedulePort(
                Optional.of(
                    leaseAt(
                        scheduledFor
                    )
                )
            );

        ScheduleProcessingRunUseCase useCase =
            newUseCase(
                schedulePort,
                new FakeRunRepository(),
                new FakeJobQueue(),
                new RecordingTransactionPort()
            );

        ScheduledProcessingRun result =
            useCase.execute(
                "amazon-deals",
                "scheduler-1"
            ).orElseThrow();

        assertEquals(
            scheduledFor.plus(
                INTERVAL
            ),
            result.nextRunAt()
        );
    }

    @Test
    void shouldNormalizeScheduledInstantInRunKey() {

        OffsetDateTime scheduledFor =
            OffsetDateTime.parse(
                "2026-09-27T12:00:00-03:00"
            );

        FakeRunRepository runRepository =
            new FakeRunRepository();

        ScheduleProcessingRunUseCase useCase =
            newUseCase(
                new FakeSchedulePort(
                    Optional.of(
                        leaseAt(
                            scheduledFor
                        )
                    )
                ),
                runRepository,
                new FakeJobQueue(),
                new RecordingTransactionPort()
            );

        useCase.execute(
            "amazon-deals",
            "scheduler-1"
        ).orElseThrow();

        assertEquals(
            "scheduled:amazon-deals:2026-09-27T15:00:00Z",
            runRepository.lastRequested.runKey()
        );
    }

    @Test
    void shouldRejectBlankScheduleKey() {

        ScheduleProcessingRunUseCase useCase =
            newUseCase(
                new FakeSchedulePort(
                    Optional.empty()
                ),
                new FakeRunRepository(),
                new FakeJobQueue(),
                new RecordingTransactionPort()
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                " ",
                "scheduler-1"
            )
        );
    }

    @Test
    void shouldRejectBlankSchedulerInstanceId() {

        ScheduleProcessingRunUseCase useCase =
            newUseCase(
                new FakeSchedulePort(
                    Optional.empty()
                ),
                new FakeRunRepository(),
                new FakeJobQueue(),
                new RecordingTransactionPort()
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                "amazon-deals",
                " "
            )
        );
    }

    @Test
    void shouldRejectInvalidCollectionMaxAttempts() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ScheduleProcessingRunUseCase(
                new FakeSchedulePort(
                    Optional.empty()
                ),
                new FakeRunRepository(),
                new FakeJobQueue(),
                new RecordingTransactionPort(),
                CLOCK,
                LEASE_DURATION,
                0
            )
        );
    }

    @Test
    void shouldRejectInvalidLeaseDuration() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ScheduleProcessingRunUseCase(
                new FakeSchedulePort(
                    Optional.empty()
                ),
                new FakeRunRepository(),
                new FakeJobQueue(),
                new RecordingTransactionPort(),
                CLOCK,
                Duration.ZERO,
                5
            )
        );
    }

    @Test
    void scheduledProcessingRunShouldRejectInvalidIds() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ScheduledProcessingRun(
                "amazon-deals",
                NOW,
                0,
                200L,
                NOW.plusMinutes(
                    15
                )
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ScheduledProcessingRun(
                "amazon-deals",
                NOW,
                100L,
                0,
                NOW.plusMinutes(
                    15
                )
            )
        );
    }

    private ScheduleProcessingRunUseCase newUseCase(
        ProcessingSchedulePort schedulePort,
        ProcessingRunRepositoryPort runRepository,
        ProcessingJobQueuePort jobQueue,
        TransactionPort transactionPort
    ) {

        return new ScheduleProcessingRunUseCase(
            schedulePort,
            runRepository,
            jobQueue,
            transactionPort,
            CLOCK,
            LEASE_DURATION,
            5
        );
    }

    private ProcessingScheduleLease leaseAt(
        OffsetDateTime scheduledFor
    ) {

        return new ProcessingScheduleLease(
            "amazon-deals",
            SOURCE,
            INTERVAL,
            scheduledFor,
            "scheduler-1",
            NOW.plus(
                LEASE_DURATION
            )
        );
    }

    private static final class RecordingTransactionPort
        implements TransactionPort {

        private int executions;

        @Override
        public <T> T execute(
            Supplier<T> operation
        ) {

            executions++;

            return operation.get();
        }
    }

    private static final class FakeSchedulePort
        implements ProcessingSchedulePort {

        private final Optional<ProcessingScheduleLease> lease;

        private int confirmCalls;

        private OffsetDateTime confirmedNextRunAt;

        private FakeSchedulePort(
            Optional<ProcessingScheduleLease> lease
        ) {

            this.lease =
                lease;
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

            return Optional.empty();
        }

        @Override
        public Optional<ProcessingScheduleLease> tryAcquireDue(
            String scheduleKey,
            String leaseOwner,
            OffsetDateTime now,
            Duration leaseDuration
        ) {

            return lease;
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

            confirmCalls++;

            confirmedNextRunAt =
                nextRunAt;

            return new ProcessingSchedule(
                scheduleKey,
                SOURCE,
                true,
                INTERVAL,
                nextRunAt,
                null,
                null,
                scheduledFor,
                processingRunId,
                NOW.minusDays(
                    1
                ),
                confirmedAt
            );
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

    private static final class FakeRunRepository
        implements ProcessingRunRepositoryPort {

        private int saveCalls;

        private ProcessingRun lastRequested;

        @Override
        public ProcessingRun save(
            ProcessingRun run
        ) {

            saveCalls++;

            lastRequested =
                run;

            return new ProcessingRun(
                100L,
                run.runKey(),
                run.source(),
                run.status(),
                run.requestedAt(),
                run.startedAt(),
                run.completedAt(),
                run.lastErrorCode(),
                run.lastErrorMessage()
            );
        }

        @Override
        public Optional<ProcessingRun> findById(
            long id
        ) {

            return Optional.empty();
        }

        @Override
        public ProcessingRun markRunning(
            long id,
            OffsetDateTime startedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingRun markCompleted(
            long id,
            OffsetDateTime completedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingRun markFailed(
            long id,
            String errorCode,
            String errorMessage,
            OffsetDateTime failedAt
        ) {

            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeJobQueue
        implements ProcessingJobQueuePort {

        private int enqueueCalls;

        private ProcessingJobSubmission lastSubmission;

        @Override
        public ProcessingJob enqueue(
            ProcessingJobSubmission submission
        ) {

            enqueueCalls++;

            lastSubmission =
                submission;

            return new ProcessingJob(
                200L,
                ProcessingJobType.COLLECT_DEALS,
                ProcessingJobStatus.PENDING,
                submission.processingRunId(),
                null,
                null,
                submission.idempotencyKey(),
                0,
                submission.maxAttempts(),
                submission.availableAt(),
                null,
                null,
                null,
                null,
                null,
                NOW,
                NOW,
                null
            );
        }

        @Override
        public Optional<ProcessingJob> claimNext(
            String workerId,
            OffsetDateTime claimedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingJob markSucceeded(
            long jobId,
            String workerId,
            OffsetDateTime finishedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingJob scheduleRetry(
            long jobId,
            String workerId,
            ProcessingFailure failure,
            OffsetDateTime availableAt,
            OffsetDateTime failedAt
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessingJob markDead(
            long jobId,
            String workerId,
            ProcessingFailure failure,
            OffsetDateTime failedAt
        ) {

            throw new UnsupportedOperationException();
        }
    }
}
