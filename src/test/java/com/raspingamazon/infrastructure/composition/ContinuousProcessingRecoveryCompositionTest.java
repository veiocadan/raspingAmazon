package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryResult;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxQueuePort;
import com.raspingamazon.infrastructure.config.ContinuousProcessingRecoveryConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContinuousProcessingRecoveryCompositionTest {

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-10-04T02:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldWireConfiguredRecoveryBoundariesAndPreserveDispatchHandler() {

        List<String> sequence =
            new ArrayList<>();

        AtomicLong processingJobBoundary =
            new AtomicLong();

        AtomicInteger processingJobBatch =
            new AtomicInteger();

        AtomicLong publicationOutboxBoundary =
            new AtomicLong();

        AtomicInteger reconciliationLimit =
            new AtomicInteger();

        AtomicLong dispatchedRunId =
            new AtomicLong();

        ContinuousProcessingRecoveryConfig config =
            new ContinuousProcessingRecoveryConfig(
                Duration.ofMinutes(
                    30
                ),
                25,
                Duration.ofMinutes(
                    10
                ),
                5,
                40
            );

        ContinuousProcessingRecoveryComposition.Components components =
            ContinuousProcessingRecoveryComposition.create(
                config,
                CLOCK,
                dispatchedRunId::set,
                (
                    leaseExpiredBefore,
                    recoveredAt,
                    batchSize
                ) -> {

                    sequence.add(
                        "processing-jobs"
                    );

                    processingJobBoundary.set(
                        Duration.between(
                            leaseExpiredBefore,
                            recoveredAt
                        ).toMinutes()
                    );

                    processingJobBatch.set(
                        batchSize
                    );

                    return new ProcessingJobLeaseRecoveryResult(
                        0,
                        0
                    );
                },
                outboxQueue(
                    sequence,
                    publicationOutboxBoundary
                ),
                limit -> {

                    sequence.add(
                        "publication-dispatch"
                    );

                    reconciliationLimit.set(
                        limit
                    );

                    return List.of();
                },
                processingRunId ->
                    Optional.empty(),
                unusedJobQueue()
            );

        assertNotNull(
            components.startupRecoveryService()
        );

        components
            .startupRecoveryService()
            .recover();

        assertEquals(
            List.of(
                "publication-outbox",
                "processing-jobs",
                "publication-dispatch"
            ),
            sequence
        );

        assertEquals(
            30L,
            processingJobBoundary.get()
        );

        assertEquals(
            25,
            processingJobBatch.get()
        );

        assertEquals(
            10L,
            publicationOutboxBoundary.get()
        );

        assertEquals(
            40,
            reconciliationLimit.get()
        );

        components
            .publicationDispatchHandler()
            .accept(
                123L
            );

        assertEquals(
            123L,
            dispatchedRunId.get()
        );
    }

    @Test
    void shouldCreateValidatedComponents() {

        ContinuousProcessingRecoveryComposition.Components components =
            ContinuousProcessingRecoveryComposition.create(
                new ContinuousProcessingRecoveryConfig(
                    Duration.ofMinutes(
                        15
                    ),
                    100,
                    Duration.ofMinutes(
                        5
                    ),
                    3,
                    100
                ),
                CLOCK,
                processingRunId -> {
                },
                (
                    leaseExpiredBefore,
                    recoveredAt,
                    batchSize
                ) ->
                    new ProcessingJobLeaseRecoveryResult(
                        0,
                        0
                    ),
                outboxQueue(
                    new ArrayList<>(),
                    new AtomicLong()
                ),
                limit ->
                    List.of(),
                processingRunId ->
                    Optional.empty(),
                unusedJobQueue()
            );

        assertNotNull(
            components.publicationDispatchHandler()
        );

        assertNotNull(
            components.startupRecoveryService()
        );
    }

    private PublicationOutboxQueuePort outboxQueue(
        List<String> sequence,
        AtomicLong boundaryMinutes
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

                boundaryMinutes.set(
                    Duration.between(
                        lockedBefore,
                        recoveredAt
                    ).toMinutes()
                );

                return 0;
            }
        };
    }

    private ProcessingJobQueuePort unusedJobQueue() {

        return new ProcessingJobQueuePort() {

            @Override
            public ProcessingJob enqueue(
                ProcessingJobSubmission submission
            ) {

                throw new AssertionError(
                    "No reconciliation candidate was returned"
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
