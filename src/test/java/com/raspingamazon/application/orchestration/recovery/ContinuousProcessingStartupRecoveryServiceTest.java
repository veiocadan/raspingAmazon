package com.raspingamazon.application.orchestration.recovery;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryResult;
import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryService;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContinuousProcessingStartupRecoveryServiceTest {

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-10-04T01:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldRecoverInExternalBoundaryJobThenReconciliationOrder() {

        List<String> sequence =
            new ArrayList<>();

        ContinuousProcessingStartupRecoveryService service =
            service(
                sequence,
                2,
                new ProcessingJobLeaseRecoveryResult(
                    3,
                    1
                )
            );

        ContinuousProcessingStartupRecoveryResult result =
            service.recover();

        assertEquals(
            List.of(
                "publication-outbox",
                "processing-jobs",
                "publication-dispatch"
            ),
            sequence
        );

        assertEquals(
            2,
            result.publicationOutboxRecovery()
                .recoveredLeaseCount()
        );

        assertEquals(
            4,
            result.processingJobRecovery()
                .totalRecovered()
        );

        assertEquals(
            0,
            result.publicationDispatchReconciliation()
                .inspectedRunCount()
        );
    }

    @Test
    void publicationOutboxFailureShouldStopStartupRecoveryImmediately() {

        List<String> sequence =
            new ArrayList<>();

        PublicationOutboxLeaseRecoveryService outboxRecovery =
            new PublicationOutboxLeaseRecoveryService(
                publicationQueue(
                    sequence,
                    true,
                    0
                ),
                Duration.ofMinutes(
                    15
                ),
                CLOCK
            );

        ContinuousProcessingStartupRecoveryService service =
            new ContinuousProcessingStartupRecoveryService(
                outboxRecovery,
                processingJobRecovery(
                    sequence,
                    false,
                    new ProcessingJobLeaseRecoveryResult(
                        0,
                        0
                    )
                ),
                reconciliation(
                    sequence
                ),
                100
            );

        assertThrows(
            IllegalStateException.class,
            service::recover
        );

        assertEquals(
            List.of(
                "publication-outbox"
            ),
            sequence
        );
    }

    @Test
    void processingJobFailureShouldPreventDispatchReconciliation() {

        List<String> sequence =
            new ArrayList<>();

        ContinuousProcessingStartupRecoveryService service =
            new ContinuousProcessingStartupRecoveryService(
                new PublicationOutboxLeaseRecoveryService(
                    publicationQueue(
                        sequence,
                        false,
                        0
                    ),
                    Duration.ofMinutes(
                        15
                    ),
                    CLOCK
                ),
                processingJobRecovery(
                    sequence,
                    true,
                    new ProcessingJobLeaseRecoveryResult(
                        0,
                        0
                    )
                ),
                reconciliation(
                    sequence
                ),
                100
            );

        assertThrows(
            IllegalStateException.class,
            service::recover
        );

        assertEquals(
            List.of(
                "publication-outbox",
                "processing-jobs"
            ),
            sequence
        );
    }

    @Test
    void shouldRejectInvalidReconciliationLimit() {

        List<String> sequence =
            new ArrayList<>();

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContinuousProcessingStartupRecoveryService(
                    new PublicationOutboxLeaseRecoveryService(
                        publicationQueue(
                            sequence,
                            false,
                            0
                        ),
                        Duration.ofMinutes(
                            15
                        ),
                        CLOCK
                    ),
                    processingJobRecovery(
                        sequence,
                        false,
                        new ProcessingJobLeaseRecoveryResult(
                            0,
                            0
                        )
                    ),
                    reconciliation(
                        sequence
                    ),
                    0
                )
        );
    }

    private ContinuousProcessingStartupRecoveryService service(
        List<String> sequence,
        int recoveredOutboxes,
        ProcessingJobLeaseRecoveryResult processingJobResult
    ) {

        return new ContinuousProcessingStartupRecoveryService(
            new PublicationOutboxLeaseRecoveryService(
                publicationQueue(
                    sequence,
                    false,
                    recoveredOutboxes
                ),
                Duration.ofMinutes(
                    15
                ),
                CLOCK
            ),
            processingJobRecovery(
                sequence,
                false,
                processingJobResult
            ),
            reconciliation(
                sequence
            ),
            100
        );
    }

    private PublicationOutboxQueuePort publicationQueue(
        List<String> sequence,
        boolean fail,
        int recovered
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

                if (fail) {

                    throw new IllegalStateException(
                        "publication outbox recovery failed"
                    );
                }

                return recovered;
            }
        };
    }

    private ProcessingJobLeaseRecoveryService processingJobRecovery(
        List<String> sequence,
        boolean fail,
        ProcessingJobLeaseRecoveryResult result
    ) {

        return new ProcessingJobLeaseRecoveryService(
            (
                leaseExpiredBefore,
                recoveredAt,
                batchSize
            ) -> {

                sequence.add(
                    "processing-jobs"
                );

                if (fail) {

                    throw new IllegalStateException(
                        "processing job recovery failed"
                    );
                }

                return result;
            },
            Duration.ofMinutes(
                30
            ),
            50,
            CLOCK
        );
    }

    private PublicationDispatchReconciliationService reconciliation(
        List<String> sequence
    ) {

        return new PublicationDispatchReconciliationService(
            limit -> {

                sequence.add(
                    "publication-dispatch"
                );

                return List.of();
            },
            processingRunId ->
                Optional.empty(),
            unusedProcessingJobQueue(),
            CLOCK,
            5
        );
    }

    private ProcessingJobQueuePort unusedProcessingJobQueue() {

        return new ProcessingJobQueuePort() {

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
