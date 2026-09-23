package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingJobFailureHandlerTest {

    private static final String WORKER_ID =
        "worker-01";

    private static final OffsetDateTime CREATED_AT =
        OffsetDateTime.parse(
            "2026-09-22T22:45:00Z"
        );

    private static final OffsetDateTime LOCKED_AT =
        OffsetDateTime.parse(
            "2026-09-22T23:40:00Z"
        );

    private static final OffsetDateTime FAILED_AT =
        OffsetDateTime.parse(
            "2026-09-22T23:45:00Z"
        );

    @Test
    void shouldScheduleRetryForTransientFailure() {

        RecordingQueue queue =
            new RecordingQueue();

        ProcessingFailureClassifier classifier =
            failure ->
                new FailureClassification(
                    ProcessingFailureType.TRANSIENT,
                    "NETWORK_TIMEOUT",
                    "request timed out"
                );

        RetryBackoffPolicy backoffPolicy =
            attemptCount -> {

                assertEquals(
                    2,
                    attemptCount
                );

                return Duration.ofMinutes(
                    2
                );
            };

        ProcessingJobFailureHandler handler =
            new ProcessingJobFailureHandler(
                classifier,
                backoffPolicy,
                queue
            );

        ProcessingJob job =
            runningJob(
                2,
                5
            );

        ProcessingJob result =
            handler.handle(
                job,
                WORKER_ID,
                new RuntimeException(
                    "boom"
                ),
                FAILED_AT
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
            job.id(),
            queue.lastJobId
        );

        assertEquals(
            WORKER_ID,
            queue.lastWorkerId
        );

        assertEquals(
            FAILED_AT.plusMinutes(
                2
            ),
            queue.lastAvailableAt
        );

        assertEquals(
            FAILED_AT,
            queue.lastFailedAt
        );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            queue.lastFailure.type()
        );

        assertEquals(
            "NETWORK_TIMEOUT",
            queue.lastFailure.errorCode()
        );

        assertEquals(
            "request timed out",
            queue.lastFailure.errorMessage()
        );

        assertEquals(
            ProcessingJobStatus.RETRY_WAIT,
            result.status()
        );
    }

    @Test
    void shouldMarkPermanentFailureAsDead() {

        RecordingQueue queue =
            new RecordingQueue();

        ProcessingFailureClassifier classifier =
            failure ->
                new FailureClassification(
                    ProcessingFailureType.PERMANENT,
                    "INVALID_PROCESSING_INPUT",
                    "invalid candidate"
                );

        RetryBackoffPolicy backoffPolicy =
            attemptCount -> {
                throw new AssertionError(
                    "backoff must not be calculated"
                );
            };

        ProcessingJobFailureHandler handler =
            new ProcessingJobFailureHandler(
                classifier,
                backoffPolicy,
                queue
            );

        ProcessingJob job =
            runningJob(
                1,
                5
            );

        ProcessingJob result =
            handler.handle(
                job,
                WORKER_ID,
                new IllegalArgumentException(
                    "invalid candidate"
                ),
                FAILED_AT
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
            ProcessingFailureType.PERMANENT,
            queue.lastFailure.type()
        );

        assertEquals(
            "INVALID_PROCESSING_INPUT",
            queue.lastFailure.errorCode()
        );

        assertEquals(
            "invalid candidate",
            queue.lastFailure.errorMessage()
        );

        assertEquals(
            ProcessingJobStatus.DEAD,
            result.status()
        );
    }

    @Test
    void shouldMarkTransientFailureAsDeadWhenAttemptsAreExhausted() {

        RecordingQueue queue =
            new RecordingQueue();

        ProcessingFailureClassifier classifier =
            failure ->
                new FailureClassification(
                    ProcessingFailureType.TRANSIENT,
                    "DATABASE_TRANSIENT",
                    "database unavailable"
                );

        RetryBackoffPolicy backoffPolicy =
            attemptCount -> {
                throw new AssertionError(
                    "backoff must not be calculated after max attempts"
                );
            };

        ProcessingJobFailureHandler handler =
            new ProcessingJobFailureHandler(
                classifier,
                backoffPolicy,
                queue
            );

        ProcessingJob job =
            runningJob(
                5,
                5
            );

        ProcessingJob result =
            handler.handle(
                job,
                WORKER_ID,
                new RuntimeException(
                    "database unavailable"
                ),
                FAILED_AT
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
            ProcessingFailureType.TRANSIENT,
            queue.lastFailure.type()
        );

        assertEquals(
            "DATABASE_TRANSIENT",
            queue.lastFailure.errorCode()
        );

        assertEquals(
            "database unavailable",
            queue.lastFailure.errorMessage()
        );

        assertEquals(
            ProcessingJobStatus.DEAD,
            result.status()
        );
    }

    @Test
    void shouldUseCurrentAttemptNumberForBackoff() {

        RecordingQueue queue =
            new RecordingQueue();

        ProcessingFailureClassifier classifier =
            failure ->
                new FailureClassification(
                    ProcessingFailureType.TRANSIENT,
                    "TEST_TRANSIENT",
                    "temporary failure"
                );

        RetryBackoffPolicy backoffPolicy =
            attemptCount -> {

                assertEquals(
                    4,
                    attemptCount
                );

                return Duration.ofSeconds(
                    240
                );
            };

        ProcessingJobFailureHandler handler =
            new ProcessingJobFailureHandler(
                classifier,
                backoffPolicy,
                queue
            );

        handler.handle(
            runningJob(
                4,
                5
            ),
            WORKER_ID,
            new RuntimeException(
                "temporary failure"
            ),
            FAILED_AT
        );

        assertEquals(
            FAILED_AT.plusSeconds(
                240
            ),
            queue.lastAvailableAt
        );
    }

    @Test
    void shouldRejectBlankWorkerIdBeforeUpdatingQueue() {

        RecordingQueue queue =
            new RecordingQueue();

        ProcessingJobFailureHandler handler =
            new ProcessingJobFailureHandler(
                failure ->
                    new FailureClassification(
                        ProcessingFailureType.PERMANENT,
                        "TEST",
                        "test"
                    ),
                attemptCount ->
                    Duration.ofSeconds(
                        30
                    ),
                queue
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> handler.handle(
                runningJob(
                    1,
                    5
                ),
                " ",
                new RuntimeException(
                    "test"
                ),
                FAILED_AT
            )
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
            CREATED_AT,
            LOCKED_AT,
            WORKER_ID,
            null,
            null,
            null,
            CREATED_AT,
            LOCKED_AT,
            null
        );
    }

    private static final class RecordingQueue
        implements ProcessingJobQueuePort {

        private int retryCalls;

        private int deadCalls;

        private long lastJobId;

        private String lastWorkerId;

        private ProcessingFailure lastFailure;

        private OffsetDateTime lastAvailableAt;

        private OffsetDateTime lastFailedAt;

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

            retryCalls++;

            lastJobId =
                jobId;

            lastWorkerId =
                workerId;

            lastFailure =
                failure;

            lastAvailableAt =
                availableAt;

            lastFailedAt =
                failedAt;

            return retryWaitJob(
                jobId,
                failure,
                availableAt,
                failedAt
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

            lastJobId =
                jobId;

            lastWorkerId =
                workerId;

            lastFailure =
                failure;

            lastAvailableAt =
                null;

            lastFailedAt =
                failedAt;

            return deadJob(
                jobId,
                failure,
                failedAt
            );
        }

        private ProcessingJob retryWaitJob(
            long jobId,
            ProcessingFailure failure,
            OffsetDateTime availableAt,
            OffsetDateTime failedAt
        ) {

            return new ProcessingJob(
                jobId,
                ProcessingJobType.ENRICH_DEAL,
                ProcessingJobStatus.RETRY_WAIT,
                null,
                200L,
                null,
                "enrich:200",
                2,
                5,
                availableAt,
                null,
                null,
                failure.type(),
                failure.errorCode(),
                failure.errorMessage(),
                CREATED_AT,
                failedAt,
                null
            );
        }

        private ProcessingJob deadJob(
            long jobId,
            ProcessingFailure failure,
            OffsetDateTime failedAt
        ) {

            return new ProcessingJob(
                jobId,
                ProcessingJobType.ENRICH_DEAL,
                ProcessingJobStatus.DEAD,
                null,
                200L,
                null,
                "enrich:200",
                5,
                5,
                CREATED_AT,
                null,
                null,
                failure.type(),
                failure.errorCode(),
                failure.errorMessage(),
                CREATED_AT,
                failedAt,
                failedAt
            );
        }
    }
}
