package com.raspingamazon.application.orchestration.worker;

import com.raspingamazon.application.observability.OperationalLogEvent;
import com.raspingamazon.application.observability.OperationalLogLevel;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;
import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import com.raspingamazon.application.orchestration.failure.ProcessingJobFailureHandler;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingWorkerTest {

    private static final String WORKER_ID =
        "worker-01";

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-23T00:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-23T00:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldReturnIdleWhenNoJobIsAvailable() {

        RecordingQueue queue =
            new RecordingQueue();

        RecordingOperationalLog operationalLog =
            new RecordingOperationalLog();

        ProcessingJobExecutionPort executor =
            job -> {
                throw new AssertionError(
                    "executor must not run"
                );
            };

        ProcessingWorker worker =
            new ProcessingWorker(
                WORKER_ID,
                queue,
                executor,
                failureHandler(
                    queue
                ),
                CLOCK,
                operationalLog
            );

        ProcessingWorkerRunResult result =
            worker.runOnce();

        assertFalse(
            result.jobClaimed()
        );

        assertEquals(
            null,
            result.claimedJobId()
        );

        assertEquals(
            null,
            result.finalStatus()
        );

        assertEquals(
            1,
            queue.claimCalls
        );

        assertEquals(
            WORKER_ID,
            queue.lastClaimWorkerId
        );

        assertEquals(
            NOW,
            queue.lastClaimedAt
        );

        /*
         * Ausência de trabalho não gera log.
         *
         * Isso evita volume inútil quando uma composição futura
         * executar runOnce() de forma recorrente.
         */
        assertTrue(
            operationalLog.events.isEmpty()
        );
    }

    @Test
    void shouldExecuteMarkSucceededAndEmitStructuredLifecycleEvents() {

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextJob =
            runningJob(
                1,
                5
            );

        RecordingOperationalLog operationalLog =
            new RecordingOperationalLog();

        AtomicInteger executionCalls =
            new AtomicInteger();

        ProcessingJobExecutionPort executor =
            job -> {

                executionCalls.incrementAndGet();

                assertEquals(
                    queue.nextJob.id(),
                    job.id()
                );
            };

        ProcessingWorker worker =
            new ProcessingWorker(
                WORKER_ID,
                queue,
                executor,
                failureHandler(
                    queue
                ),
                CLOCK,
                operationalLog
            );

        ProcessingWorkerRunResult result =
            worker.runOnce();

        assertTrue(
            result.jobClaimed()
        );

        assertEquals(
            100L,
            result.claimedJobId()
        );

        assertEquals(
            ProcessingJobStatus.SUCCEEDED,
            result.finalStatus()
        );

        assertEquals(
            1,
            executionCalls.get()
        );

        assertEquals(
            1,
            queue.successCalls
        );

        assertEquals(
            0,
            queue.retryCalls
        );

        assertEquals(
            0,
            queue.deadCalls
        );

        assertEquals(
            2,
            operationalLog.events.size()
        );

        OperationalLogEvent claimedEvent =
            operationalLog.events.get(
                0
            );

        assertEquals(
            OperationalLogLevel.INFO,
            claimedEvent.level()
        );

        assertEquals(
            "processing.job.claimed",
            claimedEvent.event()
        );

        assertEquals(
            "processing-worker",
            claimedEvent.component()
        );

        assertEquals(
            "execute-job",
            claimedEvent.operation()
        );

        assertEquals(
            "CLAIMED",
            claimedEvent.outcome()
        );

        assertEquals(
            100L,
            claimedEvent.context()
                .jobId()
        );

        assertEquals(
            ProcessingJobType.ENRICH_DEAL,
            claimedEvent.context()
                .jobType()
        );

        assertEquals(
            200L,
            claimedEvent.context()
                .candidateId()
        );

        assertNull(
            claimedEvent.context()
                .runId()
        );

        assertNull(
            claimedEvent.context()
                .snapshotId()
        );

        assertNull(
            claimedEvent.context()
                .evaluationId()
        );

        assertNull(
            claimedEvent.context()
                .publicationId()
        );

        assertNull(
            claimedEvent.context()
                .asin()
        );

        assertNull(
            claimedEvent.context()
                .integration()
        );

        assertNull(
            claimedEvent.durationMs()
        );

        assertNull(
            claimedEvent.failureOrigin()
        );

        assertNull(
            claimedEvent.failureType()
        );

        assertNull(
            claimedEvent.errorCode()
        );

        OperationalLogEvent succeededEvent =
            operationalLog.events.get(
                1
            );

        assertEquals(
            OperationalLogLevel.INFO,
            succeededEvent.level()
        );

        assertEquals(
            "processing.job.succeeded",
            succeededEvent.event()
        );

        assertEquals(
            "SUCCEEDED",
            succeededEvent.outcome()
        );

        assertEquals(
            100L,
            succeededEvent.context()
                .jobId()
        );

        assertEquals(
            ProcessingJobType.ENRICH_DEAL,
            succeededEvent.context()
                .jobType()
        );

        assertEquals(
            200L,
            succeededEvent.context()
                .candidateId()
        );

        assertNull(
            succeededEvent.failureOrigin()
        );

        assertNull(
            succeededEvent.failureType()
        );

        assertNull(
            succeededEvent.errorCode()
        );
    }

    @Test
    void shouldScheduleRetryAndEmitStructuredRetryEvent() {

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextJob =
            runningJob(
                1,
                5
            );

        RecordingOperationalLog operationalLog =
            new RecordingOperationalLog();

        ProcessingJobExecutionPort executor =
            job -> {
                throw new RuntimeException(
                    "temporary"
                );
            };

        ProcessingJobFailureHandler failureHandler =
            new ProcessingJobFailureHandler(
                failure ->
                    new FailureClassification(
                        ProcessingFailureType.TRANSIENT,
                        "TEMPORARY_FAILURE",
                        failure.getMessage()
                    ),
                attemptCount ->
                    java.time.Duration.ofSeconds(
                        30
                    ),
                queue
            );

        ProcessingWorker worker =
            new ProcessingWorker(
                WORKER_ID,
                queue,
                executor,
                failureHandler,
                CLOCK,
                operationalLog
            );

        ProcessingWorkerRunResult result =
            worker.runOnce();

        assertTrue(
            result.jobClaimed()
        );

        assertEquals(
            ProcessingJobStatus.RETRY_WAIT,
            result.finalStatus()
        );

        assertEquals(
            0,
            queue.successCalls
        );

        assertEquals(
            1,
            queue.retryCalls
        );

        assertEquals(
            0,
            queue.deadCalls
        );

        assertEquals(
            2,
            operationalLog.events.size()
        );

        OperationalLogEvent retryEvent =
            operationalLog.events.get(
                1
            );

        assertEquals(
            OperationalLogLevel.WARN,
            retryEvent.level()
        );

        assertEquals(
            "processing.job.retry-scheduled",
            retryEvent.event()
        );

        assertEquals(
            "RETRY_WAIT",
            retryEvent.outcome()
        );

        assertEquals(
            100L,
            retryEvent.context()
                .jobId()
        );

        assertEquals(
            ProcessingJobType.ENRICH_DEAL,
            retryEvent.context()
                .jobType()
        );

        assertEquals(
            200L,
            retryEvent.context()
                .candidateId()
        );

        /*
         * O worker conhece o resultado RETRY_WAIT, mas não possui
         * contexto suficiente para determinar a origem concreta da
         * falha.
         *
         * A classificação completa será observada na fronteira de
         * integração correspondente.
         */
        assertNull(
            retryEvent.failureOrigin()
        );

        assertNull(
            retryEvent.failureType()
        );

        assertNull(
            retryEvent.errorCode()
        );
    }

    @Test
    void shouldMarkDeadAndEmitStructuredDeadEvent() {

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextJob =
            runningJob(
                1,
                5
            );

        RecordingOperationalLog operationalLog =
            new RecordingOperationalLog();

        ProcessingJobExecutionPort executor =
            job -> {
                throw new IllegalArgumentException(
                    "invalid job"
                );
            };

        ProcessingJobFailureHandler failureHandler =
            new ProcessingJobFailureHandler(
                failure ->
                    new FailureClassification(
                        ProcessingFailureType.PERMANENT,
                        "INVALID_JOB",
                        failure.getMessage()
                    ),
                attemptCount -> {
                    throw new AssertionError(
                        "backoff must not run"
                    );
                },
                queue
            );

        ProcessingWorker worker =
            new ProcessingWorker(
                WORKER_ID,
                queue,
                executor,
                failureHandler,
                CLOCK,
                operationalLog
            );

        ProcessingWorkerRunResult result =
            worker.runOnce();

        assertEquals(
            ProcessingJobStatus.DEAD,
            result.finalStatus()
        );

        assertEquals(
            0,
            queue.successCalls
        );

        assertEquals(
            0,
            queue.retryCalls
        );

        assertEquals(
            1,
            queue.deadCalls
        );

        assertEquals(
            2,
            operationalLog.events.size()
        );

        OperationalLogEvent deadEvent =
            operationalLog.events.get(
                1
            );

        assertEquals(
            OperationalLogLevel.ERROR,
            deadEvent.level()
        );

        assertEquals(
            "processing.job.dead",
            deadEvent.event()
        );

        assertEquals(
            "DEAD",
            deadEvent.outcome()
        );

        assertEquals(
            100L,
            deadEvent.context()
                .jobId()
        );

        assertEquals(
            ProcessingJobType.ENRICH_DEAL,
            deadEvent.context()
                .jobType()
        );

        assertEquals(
            200L,
            deadEvent.context()
                .candidateId()
        );

        assertNull(
            deadEvent.failureOrigin()
        );

        assertNull(
            deadEvent.failureType()
        );

        assertNull(
            deadEvent.errorCode()
        );
    }

    @Test
    void shouldNotConvertAcknowledgementFailureIntoProcessingRetry() {

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextJob =
            runningJob(
                1,
                5
            );

        queue.failMarkSucceeded =
            true;

        RecordingOperationalLog operationalLog =
            new RecordingOperationalLog();

        ProcessingWorker worker =
            new ProcessingWorker(
                WORKER_ID,
                queue,
                job -> {
                },
                failureHandler(
                    queue
                ),
                CLOCK,
                operationalLog
            );

        assertThrows(
            IllegalStateException.class,
            worker::runOnce
        );

        assertEquals(
            1,
            queue.successCalls
        );

        assertEquals(
            0,
            queue.retryCalls
        );

        assertEquals(
            0,
            queue.deadCalls
        );

        /*
         * O claim foi persistido e observado.
         *
         * O evento de sucesso não existe porque markSucceeded
         * falhou antes da confirmação persistente.
         */
        assertEquals(
            1,
            operationalLog.events.size()
        );

        assertEquals(
            "processing.job.claimed",
            operationalLog.events
                .getFirst()
                .event()
        );
    }

    @Test
    void shouldNotLetLoggingFailureChangeSuccessfulProcessing() {

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextJob =
            runningJob(
                1,
                5
            );

        StructuredOperationalLogPort failingLog =
            event -> {
                throw new IllegalStateException(
                    "logging unavailable"
                );
            };

        ProcessingWorker worker =
            new ProcessingWorker(
                WORKER_ID,
                queue,
                job -> {
                },
                failureHandler(
                    queue
                ),
                CLOCK,
                failingLog
            );

        ProcessingWorkerRunResult result =
            worker.runOnce();

        assertTrue(
            result.jobClaimed()
        );

        assertEquals(
            100L,
            result.claimedJobId()
        );

        assertEquals(
            ProcessingJobStatus.SUCCEEDED,
            result.finalStatus()
        );

        assertEquals(
            1,
            queue.successCalls
        );

        assertEquals(
            0,
            queue.retryCalls
        );

        assertEquals(
            0,
            queue.deadCalls
        );
    }

    @Test
    void shouldRejectNullOperationalLog() {

        RecordingQueue queue =
            new RecordingQueue();

        assertThrows(
            NullPointerException.class,
            () ->
                new ProcessingWorker(
                    WORKER_ID,
                    queue,
                    job -> {
                    },
                    failureHandler(
                        queue
                    ),
                    CLOCK,
                    null
                )
        );
    }

    private static ProcessingJobFailureHandler failureHandler(
        RecordingQueue queue
    ) {

        return new ProcessingJobFailureHandler(
            failure ->
                new FailureClassification(
                    ProcessingFailureType.PERMANENT,
                    "TEST_FAILURE",
                    failure.getMessage() == null
                        ? "failure"
                        : failure.getMessage()
                ),
            attemptCount ->
                java.time.Duration.ofSeconds(
                    30
                ),
            queue
        );
    }

    private static ProcessingJob runningJob(
        int attemptCount,
        int maxAttempts
    ) {

        return new ProcessingJob(
            100L,
            ProcessingJobType.ENRICH_DEAL,
            ProcessingJobStatus.RUNNING,
            null,
            200L,
            null,
            "enrich:200",
            attemptCount,
            maxAttempts,
            NOW.minusHours(
                1
            ),
            NOW.minusMinutes(
                1
            ),
            WORKER_ID,
            null,
            null,
            null,
            NOW.minusHours(
                1
            ),
            NOW.minusMinutes(
                1
            ),
            null
        );
    }

    private static final class RecordingOperationalLog
        implements StructuredOperationalLogPort {

        private final List<OperationalLogEvent> events =
            new ArrayList<>();

        @Override
        public void log(
            OperationalLogEvent event
        ) {

            events.add(
                event
            );
        }
    }

    private static final class RecordingQueue
        implements ProcessingJobQueuePort {

        private ProcessingJob nextJob;

        private int claimCalls;

        private int successCalls;

        private int retryCalls;

        private int deadCalls;

        private String lastClaimWorkerId;

        private OffsetDateTime lastClaimedAt;

        private boolean failMarkSucceeded;

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

            lastClaimWorkerId =
                workerId;

            lastClaimedAt =
                claimedAt;

            return Optional.ofNullable(
                nextJob
            );
        }

        @Override
        public ProcessingJob markSucceeded(
            long jobId,
            String workerId,
            OffsetDateTime finishedAt
        ) {

            successCalls++;

            if (failMarkSucceeded) {
                throw new IllegalStateException(
                    "job lease no longer belongs to worker"
                );
            }

            return terminalJob(
                ProcessingJobStatus.SUCCEEDED,
                null,
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

            retryCalls++;

            return new ProcessingJob(
                jobId,
                ProcessingJobType.ENRICH_DEAL,
                ProcessingJobStatus.RETRY_WAIT,
                null,
                200L,
                null,
                "enrich:200",
                nextJob.attemptCount(),
                nextJob.maxAttempts(),
                availableAt,
                null,
                null,
                failure.type(),
                failure.errorCode(),
                failure.errorMessage(),
                nextJob.createdAt(),
                failedAt,
                null
            );
        }

        @Override
        public ProcessingJob markDead(
            long jobId,
            String workerId,
            ProcessingFailure failure,
            OffsetDateTime failedAt
        ) {

            deadCalls++;

            return terminalJob(
                ProcessingJobStatus.DEAD,
                failure,
                failedAt
            );
        }

        private ProcessingJob terminalJob(
            ProcessingJobStatus status,
            ProcessingFailure failure,
            OffsetDateTime finishedAt
        ) {

            return new ProcessingJob(
                100L,
                ProcessingJobType.ENRICH_DEAL,
                status,
                null,
                200L,
                null,
                "enrich:200",
                nextJob.attemptCount(),
                nextJob.maxAttempts(),
                nextJob.availableAt(),
                null,
                null,
                failure == null
                    ? null
                    : failure.type(),
                failure == null
                    ? null
                    : failure.errorCode(),
                failure == null
                    ? null
                    : failure.errorMessage(),
                nextJob.createdAt(),
                finishedAt,
                finishedAt
            );
        }
    }
}
