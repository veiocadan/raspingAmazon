package com.raspingamazon.application.orchestration.recovery;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryResult;
import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryService;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.publication.ProcessingRunPublicationReadiness;
import com.raspingamazon.application.publication.PublicationDispatchReconciliationService;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxQueuePort;
import com.raspingamazon.application.publication.outbox.recovery.PublicationOutboxLeaseRecoveryService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContinuousProcessingStartupRecoveryDrainTest {

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-10-06T21:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void startupShouldDrainBothPaginatedAuthoritiesBeforeReturning() {

        List<String> sequence =
            new ArrayList<>();

        Queue<ProcessingJobLeaseRecoveryResult> jobPages =
            new ArrayDeque<>();

        jobPages.add(
            new ProcessingJobLeaseRecoveryResult(
                1,
                0
            )
        );

        jobPages.add(
            new ProcessingJobLeaseRecoveryResult(
                0,
                0
            )
        );

        Queue<List<Long>> dispatchPages =
            new ArrayDeque<>();

        dispatchPages.add(
            List.of(
                100L
            )
        );

        dispatchPages.add(
            List.of()
        );

        PublicationOutboxLeaseRecoveryService outbox =
            new PublicationOutboxLeaseRecoveryService(
                publicationQueue(
                    sequence
                ),
                Duration.ofMinutes(
                    5
                ),
                CLOCK
            );

        ProcessingJobLeaseRecoveryService jobs =
            new ProcessingJobLeaseRecoveryService(
                (
                    leaseExpiredBefore,
                    recoveredAt,
                    batchSize
                ) -> {

                    sequence.add(
                        "processing-jobs"
                    );

                    return jobPages.remove();
                },
                Duration.ofMinutes(
                    15
                ),
                1,
                CLOCK
            );

        PublicationDispatchReconciliationService reconciliation =
            new PublicationDispatchReconciliationService(
                limit -> {

                    sequence.add(
                        "publication-dispatch"
                    );

                    return dispatchPages.remove();
                },
                processingRunId ->
                    Optional.of(
                        ProcessingRunPublicationReadiness.from(
                            processingRunId,
                            ProcessingRunStatus.COMPLETED,
                            0,
                            0,
                            0,
                            0
                        )
                    ),
                echoQueue(),
                CLOCK,
                3
            );

        ContinuousProcessingStartupRecoveryResult result =
            new ContinuousProcessingStartupRecoveryService(
                outbox,
                jobs,
                reconciliation,
                1
            ).recover();

        assertEquals(
            List.of(
                "publication-outbox",
                "processing-jobs",
                "processing-jobs",
                "publication-dispatch",
                "publication-dispatch"
            ),
            sequence
        );

        assertEquals(
            1,
            result.processingJobRecovery()
                .totalRecovered()
        );

        assertEquals(
            1,
            result.publicationDispatchReconciliation()
                .enqueuedJobCount()
        );
    }

    private PublicationOutboxQueuePort publicationQueue(
        List<String> sequence
    ) {

        return new PublicationOutboxQueuePort() {

            @Override
            public Optional<PublicationOutboxItem> claimNext(
                String workerId,
                OffsetDateTime claimedAt
            ) {

                throw new UnsupportedOperationException();
            }

            @Override
            public int recoverExpiredLeases(
                OffsetDateTime lockedBefore,
                OffsetDateTime recoveredAt
            ) {

                sequence.add(
                    "publication-outbox"
                );

                return 0;
            }
        };
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
}
