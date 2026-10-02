package com.raspingamazon.application.publication;

import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.publication.port.ProcessingRunPublicationReadinessQueryPort;
import com.raspingamazon.application.publication.port.PublicationDispatchReconciliationCandidateQueryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationDispatchReconciliationServiceTest {

    private static final Instant NOW =
        Instant.parse(
            "2026-10-01T22:30:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

    @Test
    void shouldEnqueueOnlyReadyAndBlockedRuns() {

        List<ProcessingJobSubmission> submissions =
            new ArrayList<>();

        PublicationDispatchReconciliationService service =
            service(
                limit ->
                    List.of(
                        11L,
                        12L,
                        13L
                    ),
                processingRunId ->
                    Optional.of(
                        switch ((int) processingRunId) {

                            case 11 ->
                                readiness(
                                    11L,
                                    ProcessingRunPublicationReadinessStatus.READY
                                );

                            case 12 ->
                                readiness(
                                    12L,
                                    ProcessingRunPublicationReadinessStatus
                                        .IN_PROGRESS
                                );

                            case 13 ->
                                readiness(
                                    13L,
                                    ProcessingRunPublicationReadinessStatus
                                        .BLOCKED
                                );

                            default ->
                                throw new AssertionError();
                        }
                    ),
                queue(
                    submissions
                )
            );

        PublicationDispatchReconciliationResult result =
            service.reconcile(
                10
            );

        assertEquals(
            3,
            result.inspectedRunCount()
        );

        assertEquals(
            1,
            result.readyRunCount()
        );

        assertEquals(
            1,
            result.inProgressRunCount()
        );

        assertEquals(
            1,
            result.blockedRunCount()
        );

        assertEquals(
            2,
            result.enqueuedJobCount()
        );

        assertEquals(
            2,
            submissions.size()
        );

        assertSubmission(
            submissions.get(
                0
            ),
            11L
        );

        assertSubmission(
            submissions.get(
                1
            ),
            13L
        );
    }

    @Test
    void shouldDoNothingForInProgressRun() {

        List<ProcessingJobSubmission> submissions =
            new ArrayList<>();

        PublicationDispatchReconciliationService service =
            service(
                limit ->
                    List.of(
                        21L
                    ),
                processingRunId ->
                    Optional.of(
                        readiness(
                            processingRunId,
                            ProcessingRunPublicationReadinessStatus
                                .IN_PROGRESS
                        )
                    ),
                queue(
                    submissions
                )
            );

        PublicationDispatchReconciliationResult result =
            service.reconcile(
                5
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
            0,
            submissions.size()
        );
    }

    @Test
    void shouldUseStableIdempotencyKey() {

        List<ProcessingJobSubmission> submissions =
            new ArrayList<>();

        PublicationDispatchReconciliationService service =
            service(
                limit ->
                    List.of(
                        31L
                    ),
                processingRunId ->
                    Optional.of(
                        readiness(
                            processingRunId,
                            ProcessingRunPublicationReadinessStatus.READY
                        )
                    ),
                queue(
                    submissions
                )
            );

        service.reconcile(
            1
        );

        assertEquals(
            "publication-dispatch:31",
            submissions.getFirst()
                .idempotencyKey()
        );
    }

    @Test
    void shouldRejectDuplicateRunFromCandidateQuery() {

        PublicationDispatchReconciliationService service =
            service(
                limit ->
                    List.of(
                        41L,
                        41L
                    ),
                processingRunId ->
                    Optional.of(
                        readiness(
                            processingRunId,
                            ProcessingRunPublicationReadinessStatus.READY
                        )
                    ),
                queue(
                    new ArrayList<>()
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.reconcile(
                    10
                )
        );
    }

    @Test
    void shouldRejectMissingRunDuringReadinessLookup() {

        PublicationDispatchReconciliationService service =
            service(
                limit ->
                    List.of(
                        51L
                    ),
                processingRunId ->
                    Optional.empty(),
                queue(
                    new ArrayList<>()
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.reconcile(
                    10
                )
        );
    }

    @Test
    void shouldRejectInvalidLimit() {

        PublicationDispatchReconciliationService service =
            service(
                limit ->
                    List.of(),
                processingRunId ->
                    Optional.empty(),
                queue(
                    new ArrayList<>()
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.reconcile(
                    0
                )
        );
    }

    @Test
    void shouldRejectQueryReturningMoreThanLimit() {

        PublicationDispatchReconciliationService service =
            service(
                limit ->
                    List.of(
                        61L,
                        62L
                    ),
                processingRunId ->
                    Optional.of(
                        readiness(
                            processingRunId,
                            ProcessingRunPublicationReadinessStatus.READY
                        )
                    ),
                queue(
                    new ArrayList<>()
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.reconcile(
                    1
                )
        );
    }

    private PublicationDispatchReconciliationService service(
        PublicationDispatchReconciliationCandidateQueryPort candidateQueryPort,
        ProcessingRunPublicationReadinessQueryPort readinessQueryPort,
        ProcessingJobQueuePort queue
    ) {

        return new PublicationDispatchReconciliationService(
            candidateQueryPort,
            readinessQueryPort,
            queue,
            CLOCK,
            5
        );
    }

    private ProcessingRunPublicationReadiness readiness(
        long processingRunId,
        ProcessingRunPublicationReadinessStatus status
    ) {

        return switch (status) {

            case READY ->
                ProcessingRunPublicationReadiness.from(
                    processingRunId,
                    ProcessingRunStatus.COMPLETED,
                    1L,
                    1L,
                    0L,
                    0L
                );

            case IN_PROGRESS ->
                ProcessingRunPublicationReadiness.from(
                    processingRunId,
                    ProcessingRunStatus.COMPLETED,
                    1L,
                    0L,
                    1L,
                    0L
                );

            case BLOCKED ->
                ProcessingRunPublicationReadiness.from(
                    processingRunId,
                    ProcessingRunStatus.COMPLETED,
                    1L,
                    0L,
                    0L,
                    1L
                );
        };
    }

    private ProcessingJobQueuePort queue(
        List<ProcessingJobSubmission> submissions
    ) {

        return new ProcessingJobQueuePort() {

            @Override
            public ProcessingJob enqueue(
                ProcessingJobSubmission submission
            ) {

                submissions.add(
                    submission
                );

                return new ProcessingJob(
                    1000L + submission.processingRunId(),
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
                com.raspingamazon.application.orchestration.ProcessingFailure failure,
                OffsetDateTime availableAt,
                OffsetDateTime failedAt
            ) {

                throw new UnsupportedOperationException();
            }

            @Override
            public ProcessingJob markDead(
                long jobId,
                String workerId,
                com.raspingamazon.application.orchestration.ProcessingFailure failure,
                OffsetDateTime failedAt
            ) {

                throw new UnsupportedOperationException();
            }
        };
    }

    private void assertSubmission(
        ProcessingJobSubmission submission,
        long expectedProcessingRunId
    ) {

        assertEquals(
            ProcessingJobType.PUBLICATION_DISPATCH,
            submission.type()
        );

        assertEquals(
            expectedProcessingRunId,
            submission.processingRunId()
        );

        assertEquals(
            "publication-dispatch:"
                + expectedProcessingRunId,
            submission.idempotencyKey()
        );

        assertEquals(
            5,
            submission.maxAttempts()
        );

        assertEquals(
            NOW,
            submission.availableAt()
                .toInstant()
        );
    }
}
