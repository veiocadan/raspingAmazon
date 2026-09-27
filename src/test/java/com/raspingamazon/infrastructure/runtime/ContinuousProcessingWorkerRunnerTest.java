package com.raspingamazon.infrastructure.runtime;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import com.raspingamazon.application.orchestration.failure.ProcessingJobFailureHandler;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.orchestration.worker.ProcessingJobExecutionPort;
import com.raspingamazon.application.orchestration.worker.ProcessingWorker;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContinuousProcessingWorkerRunnerTest {

    private static final String WORKER_ID =
        "continuous-worker-test";

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-27T16:30:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-27T16:30:00Z"
            ),
            ZoneOffset.UTC
        );

    private static final Duration IDLE_DELAY =
        Duration.ofSeconds(
            2
        );

    @Test
    void shouldDrainAvailableJobsBeforeWaiting() {

        QueueStub queue =
            new QueueStub();

        queue.addAvailableJob(
            runningJob(
                101L
            )
        );

        queue.addAvailableJob(
            runningJob(
                102L
            )
        );

        AtomicInteger executions =
            new AtomicInteger();

        ProcessingWorker worker =
            createWorker(
                queue,
                job -> executions.incrementAndGet()
            );

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

        ContinuousProcessingWorkerRunner runner =
            new ContinuousProcessingWorkerRunner(
                worker,
                IDLE_DELAY,
                waitStrategy
            );

        runner.run(
            keepRunning::get
        );

        /*
         * Duas rodadas processam jobs.
         *
         * A terceira encontra a fila vazia.
         */
        assertEquals(
            3,
            queue.claimCalls()
        );

        assertEquals(
            2,
            executions.get()
        );

        assertEquals(
            2,
            queue.succeededJobs()
        );

        /*
         * Não houve espera entre os dois jobs.
         *
         * A espera ocorreu somente depois de a fila ficar vazia.
         */
        assertEquals(
            1,
            waitStrategy.calls()
        );

        assertEquals(
            IDLE_DELAY,
            waitStrategy.lastDuration()
        );
    }

    @Test
    void shouldKeepPollingAfterIdleRounds() {

        QueueStub queue =
            new QueueStub();

        ProcessingWorker worker =
            createWorker(
                queue,
                job -> {
                    throw new AssertionError(
                        "No job should be executed"
                    );
                }
            );

        AtomicBoolean keepRunning =
            new AtomicBoolean(
                true
            );

        AtomicInteger waits =
            new AtomicInteger();

        WorkerIdleWaitStrategy waitStrategy =
            duration -> {

                assertEquals(
                    IDLE_DELAY,
                    duration
                );

                if (waits.incrementAndGet()
                    == 2) {

                    keepRunning.set(
                        false
                    );
                }
            };

        ContinuousProcessingWorkerRunner runner =
            new ContinuousProcessingWorkerRunner(
                worker,
                IDLE_DELAY,
                waitStrategy
            );

        runner.run(
            keepRunning::get
        );

        assertEquals(
            2,
            queue.claimCalls()
        );

        assertEquals(
            2,
            waits.get()
        );
    }

    @Test
    void shouldNotRunWorkerWhenStoppedBeforeStart() {

        QueueStub queue =
            new QueueStub();

        ProcessingWorker worker =
            createWorker(
                queue,
                job -> {
                    throw new AssertionError(
                        "Worker must not execute"
                    );
                }
            );

        RecordingWaitStrategy waitStrategy =
            new RecordingWaitStrategy(
                () -> {
                }
            );

        ContinuousProcessingWorkerRunner runner =
            new ContinuousProcessingWorkerRunner(
                worker,
                IDLE_DELAY,
                waitStrategy
            );

        runner.run(
            () -> false
        );

        assertEquals(
            0,
            queue.claimCalls()
        );

        assertEquals(
            0,
            waitStrategy.calls()
        );
    }

    @Test
    void shouldStopAndRestoreInterruptFlagWhenIdleWaitIsInterrupted() {

        QueueStub queue =
            new QueueStub();

        ProcessingWorker worker =
            createWorker(
                queue,
                job -> {
                    throw new AssertionError(
                        "No job should be executed"
                    );
                }
            );

        WorkerIdleWaitStrategy interruptedWait =
            duration -> {
                throw new InterruptedException(
                    "expected test interruption"
                );
            };

        ContinuousProcessingWorkerRunner runner =
            new ContinuousProcessingWorkerRunner(
                worker,
                IDLE_DELAY,
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
                queue.claimCalls()
            );

        } finally {

            /*
             * Limpa o flag para impedir interferência nos demais
             * testes executados pela mesma thread do JUnit.
             */
            Thread.interrupted();
        }

        assertFalse(
            Thread.currentThread()
                .isInterrupted()
        );
    }

    @Test
    void shouldPropagateUnexpectedWorkerFailure() {

        ExplodingQueue queue =
            new ExplodingQueue();

        ProcessingWorker worker =
            createWorker(
                queue,
                job -> {
                    throw new AssertionError(
                        "Executor must not be reached"
                    );
                }
            );

        RecordingWaitStrategy waitStrategy =
            new RecordingWaitStrategy(
                () -> {
                }
            );

        ContinuousProcessingWorkerRunner runner =
            new ContinuousProcessingWorkerRunner(
                worker,
                IDLE_DELAY,
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
            "expected database/runtime failure",
            failure.getMessage()
        );

        /*
         * Uma falha inesperada não deve virar polling silencioso.
         */
        assertEquals(
            0,
            waitStrategy.calls()
        );
    }

    @Test
    void shouldRejectNonPositiveIdleDelay() {

        QueueStub queue =
            new QueueStub();

        ProcessingWorker worker =
            createWorker(
                queue,
                job -> {
                }
            );

        RecordingWaitStrategy waitStrategy =
            new RecordingWaitStrategy(
                () -> {
                }
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ContinuousProcessingWorkerRunner(
                worker,
                Duration.ZERO,
                waitStrategy
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ContinuousProcessingWorkerRunner(
                worker,
                Duration.ofSeconds(
                    -1
                ),
                waitStrategy
            )
        );
    }

    private ProcessingWorker createWorker(
        ProcessingJobQueuePort queue,
        ProcessingJobExecutionPort executionPort
    ) {

        ProcessingJobFailureHandler failureHandler =
            new ProcessingJobFailureHandler(
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
                        1
                    ),
                queue
            );

        return new ProcessingWorker(
            WORKER_ID,
            queue,
            executionPort,
            failureHandler,
            CLOCK
        );
    }

    private ProcessingJob runningJob(
        long id
    ) {

        return new ProcessingJob(
            id,
            ProcessingJobType.COLLECT_DEALS,
            ProcessingJobStatus.RUNNING,
            id,
            null,
            null,
            "collect:" + id,
            1,
            5,
            NOW,
            NOW,
            WORKER_ID,
            null,
            null,
            null,
            NOW.minusMinutes(
                1
            ),
            NOW,
            null
        );
    }

    private static final class RecordingWaitStrategy
        implements WorkerIdleWaitStrategy {

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

    private static class QueueStub
        implements ProcessingJobQueuePort {

        private final Deque<ProcessingJob> availableJobs =
            new ArrayDeque<>();

        private int claimCalls;

        private int succeededJobs;

        private void addAvailableJob(
            ProcessingJob job
        ) {

            availableJobs.addLast(
                job
            );
        }

        @Override
        public ProcessingJob enqueue(
            ProcessingJobSubmission submission
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProcessingJob> claimNext(
            String workerId,
            OffsetDateTime claimedAt
        ) {

            claimCalls++;

            return Optional.ofNullable(
                availableJobs.pollFirst()
            );
        }

        @Override
        public ProcessingJob markSucceeded(
            long jobId,
            String workerId,
            OffsetDateTime finishedAt
        ) {

            succeededJobs++;

            return new ProcessingJob(
                jobId,
                ProcessingJobType.COLLECT_DEALS,
                ProcessingJobStatus.SUCCEEDED,
                jobId,
                null,
                null,
                "collect:" + jobId,
                1,
                5,
                NOW,
                null,
                null,
                null,
                null,
                null,
                NOW.minusMinutes(
                    1
                ),
                finishedAt,
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

        private int claimCalls() {

            return claimCalls;
        }

        private int succeededJobs() {

            return succeededJobs;
        }
    }

    private static final class ExplodingQueue
        implements ProcessingJobQueuePort {

        @Override
        public ProcessingJob enqueue(
            ProcessingJobSubmission submission
        ) {

            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProcessingJob> claimNext(
            String workerId,
            OffsetDateTime claimedAt
        ) {

            throw new IllegalStateException(
                "expected database/runtime failure"
            );
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
