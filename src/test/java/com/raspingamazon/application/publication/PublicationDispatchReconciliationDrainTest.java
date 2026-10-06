package com.raspingamazon.application.publication;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationDispatchReconciliationDrainTest {

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-10-06T21:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldDrainFullPagesUntilPartialPage() {

        Queue<List<Long>> pages =
            new ArrayDeque<>();

        pages.add(
            List.of(
                1L,
                2L
            )
        );

        pages.add(
            List.of(
                3L
            )
        );

        AtomicInteger calls =
            new AtomicInteger();

        PublicationDispatchReconciliationService service =
            new PublicationDispatchReconciliationService(
                limit -> {

                    calls.incrementAndGet();

                    assertEquals(
                        2,
                        limit
                    );

                    return pages.remove();
                },
                processingRunId ->
                    Optional.of(
                        ready(
                            processingRunId
                        )
                    ),
                echoQueue(),
                CLOCK,
                3
            );

        PublicationDispatchReconciliationResult result =
            service.reconcileUntilQuiescent(
                2
            );

        assertEquals(
            3,
            result.inspectedRunCount()
        );

        assertEquals(
            3,
            result.readyRunCount()
        );

        assertEquals(
            0,
            result.inProgressRunCount()
        );

        assertEquals(
            3,
            result.enqueuedJobCount()
        );

        assertEquals(
            2,
            calls.get()
        );
    }

    @Test
    void shouldFailClosedOnFullPageWithoutDurableProgress() {

        PublicationDispatchReconciliationService service =
            new PublicationDispatchReconciliationService(
                limit ->
                    List.of(
                        10L,
                        11L
                    ),
                processingRunId ->
                    Optional.of(
                        inProgress(
                            processingRunId
                        )
                    ),
                unusedQueue(),
                CLOCK,
                3
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.reconcileUntilQuiescent(
                    2
                )
        );
    }

    @Test
    void partialInProgressPageShouldRepresentQuiescentSnapshot() {

        AtomicInteger calls =
            new AtomicInteger();

        PublicationDispatchReconciliationService service =
            new PublicationDispatchReconciliationService(
                limit -> {

                    calls.incrementAndGet();

                    return List.of(
                        10L
                    );
                },
                processingRunId ->
                    Optional.of(
                        inProgress(
                            processingRunId
                        )
                    ),
                unusedQueue(),
                CLOCK,
                3
            );

        PublicationDispatchReconciliationResult result =
            service.reconcileUntilQuiescent(
                2
            );

        assertEquals(
            1,
            result.inspectedRunCount()
        );

        assertEquals(
            1,
            result.inProgressRunCount()
        );

        assertEquals(
            0,
            result.enqueuedJobCount()
        );

        assertEquals(
            1,
            calls.get()
        );
    }

    private ProcessingRunPublicationReadiness ready(
        long processingRunId
    ) {

        return ProcessingRunPublicationReadiness.from(
            processingRunId,
            ProcessingRunStatus.COMPLETED,
            0,
            0,
            0,
            0
        );
    }

    private ProcessingRunPublicationReadiness inProgress(
        long processingRunId
    ) {

        return ProcessingRunPublicationReadiness.from(
            processingRunId,
            ProcessingRunStatus.COMPLETED,
            1,
            0,
            1,
            0
        );
    }

    private ProcessingJobQueuePort echoQueue() {

        return new ProcessingJobQueuePort() {

            @Override
            public ProcessingJob enqueue(
                ProcessingJobSubmission submission
            ) {

                return new ProcessingJob(
                    1L,
                    submission.type(),
                    ProcessingJobStatus.PENDING,
                    submission.processingRunId(),
                    submission.dealCandidateId(),
                    submission.offerSnapshotId(),
                    submission.idempotencyKey(),
                    0,
                    submission.maxAttempts(),
                    submission.availableAt(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    submission.availableAt(),
                    submission.availableAt(),
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
        };
    }

    private ProcessingJobQueuePort unusedQueue() {

        return new ProcessingJobQueuePort() {

            @Override
            public ProcessingJob enqueue(
                ProcessingJobSubmission submission
            ) {

                throw new AssertionError(
                    "enqueue must not be called"
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
        };
    }
}
