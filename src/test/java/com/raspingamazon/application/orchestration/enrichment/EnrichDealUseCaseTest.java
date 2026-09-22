package com.raspingamazon.application.orchestration.enrichment;

import com.raspingamazon.application.deal.OfferSnapshotFactory;
import com.raspingamazon.application.deal.PersistedOfferSnapshot;
import com.raspingamazon.application.deal.port.OfferEvidencePersistencePort;
import com.raspingamazon.application.deal.port.OfferSnapshotPersistencePort;
import com.raspingamazon.application.deal.port.PaymentConditionPersistencePort;
import com.raspingamazon.application.deal.port.ProductPersistencePort;
import com.raspingamazon.application.deal.port.TransactionPort;
import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.application.orchestration.DealCandidate;
import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.port.DealCandidateRepositoryPort;
import com.raspingamazon.application.orchestration.port.EnrichedOfferLookupPort;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnrichDealUseCaseTest {

    private static final long CANDIDATE_ID =
        100L;

    private static final long PRODUCT_ID =
        200L;

    private static final long SNAPSHOT_ID =
        300L;

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-22T23:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-22T23:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldEnrichPersistAndEnqueueEvaluation() {

        List<String> events =
            new ArrayList<>();

        DealCandidate candidate =
            createCandidate();

        DealCandidateRepositoryPort candidateRepository =
            candidateRepositoryReturning(
                candidate,
                events
            );

        EnrichedOfferLookupPort lookup =
            value -> {

                events.add(
                    "snapshot-lookup"
                );

                return OptionalLong.empty();
            };

        ProductEnrichmentClient enrichmentClient =
            parsedDeal -> {

                events.add(
                    "enrich"
                );

                return createEnrichmentResult();
            };

        ProductPersistencePort productPort =
            parsedDeal -> {

                events.add(
                    "product"
                );

                return createProduct();
            };

        OfferSnapshotPersistencePort snapshotPort =
            snapshot -> {

                events.add(
                    "snapshot"
                );

                return new PersistedOfferSnapshot(
                    copyWithId(
                        snapshot,
                        SNAPSHOT_ID
                    ),
                    true
                );
            };

        PaymentConditionPersistencePort paymentPort =
            (snapshotId, conditions) -> {

                events.add(
                    "payments"
                );

                assertEquals(
                    SNAPSHOT_ID,
                    snapshotId
                );
            };

        OfferEvidencePersistencePort evidencePort =
            (snapshotId, enrichmentResult) -> {

                events.add(
                    "evidence"
                );

                assertEquals(
                    SNAPSHOT_ID,
                    snapshotId
                );
            };

        RecordingQueue queue =
            new RecordingQueue(
                events
            );

        RecordingTransactionPort transactionPort =
            new RecordingTransactionPort(
                events
            );

        EnrichDealUseCase useCase =
            new EnrichDealUseCase(
                candidateRepository,
                lookup,
                enrichmentClient,
                productPort,
                new OfferSnapshotFactory(),
                snapshotPort,
                paymentPort,
                evidencePort,
                queue,
                transactionPort,
                CLOCK,
                5
            );

        long snapshotId =
            useCase.execute(
                CANDIDATE_ID
            );

        assertEquals(
            SNAPSHOT_ID,
            snapshotId
        );

        assertEquals(
            List.of(
                "candidate-find",
                "snapshot-lookup",
                "enrich",
                "transaction-begin",
                "product",
                "snapshot",
                "payments",
                "evidence",
                "enqueue",
                "transaction-commit"
            ),
            events
        );

        assertEquals(
            1,
            queue.submissions.size()
        );

        ProcessingJobSubmission submission =
            queue.submissions.getFirst();

        assertEquals(
            SNAPSHOT_ID,
            submission.offerSnapshotId()
        );

        assertEquals(
            "evaluate:" + SNAPSHOT_ID,
            submission.idempotencyKey()
        );

        assertEquals(
            5,
            submission.maxAttempts()
        );
    }

    @Test
    void shouldNotCallEnrichmentWhenSnapshotAlreadyExists() {

        List<String> events =
            new ArrayList<>();

        DealCandidate candidate =
            createCandidate();

        DealCandidateRepositoryPort candidateRepository =
            candidateRepositoryReturning(
                candidate,
                events
            );

        EnrichedOfferLookupPort lookup =
            value -> {

                events.add(
                    "snapshot-lookup"
                );

                return OptionalLong.of(
                    SNAPSHOT_ID
                );
            };

        ProductEnrichmentClient enrichmentClient =
            parsedDeal -> {
                throw new AssertionError(
                    "enrichment must not run"
                );
            };

        ProductPersistencePort productPort =
            parsedDeal -> {
                throw new AssertionError(
                    "product persistence must not run"
                );
            };

        OfferSnapshotPersistencePort snapshotPort =
            snapshot -> {
                throw new AssertionError(
                    "snapshot persistence must not run"
                );
            };

        PaymentConditionPersistencePort paymentPort =
            (snapshotId, conditions) -> {
                throw new AssertionError(
                    "payment persistence must not run"
                );
            };

        OfferEvidencePersistencePort evidencePort =
            (snapshotId, enrichmentResult) -> {
                throw new AssertionError(
                    "evidence persistence must not run"
                );
            };

        RecordingQueue queue =
            new RecordingQueue(
                events
            );

        RecordingTransactionPort transactionPort =
            new RecordingTransactionPort(
                events
            );

        EnrichDealUseCase useCase =
            new EnrichDealUseCase(
                candidateRepository,
                lookup,
                enrichmentClient,
                productPort,
                new OfferSnapshotFactory(),
                snapshotPort,
                paymentPort,
                evidencePort,
                queue,
                transactionPort,
                CLOCK,
                5
            );

        long snapshotId =
            useCase.execute(
                CANDIDATE_ID
            );

        assertEquals(
            SNAPSHOT_ID,
            snapshotId
        );

        assertEquals(
            List.of(
                "candidate-find",
                "snapshot-lookup",
                "transaction-begin",
                "enqueue",
                "transaction-commit"
            ),
            events
        );

        assertEquals(
            1,
            queue.submissions.size()
        );
    }

    @Test
    void shouldNotDuplicateDependenciesWhenSnapshotIsReused() {

        List<String> events =
            new ArrayList<>();

        DealCandidate candidate =
            createCandidate();

        DealCandidateRepositoryPort candidateRepository =
            candidateRepositoryReturning(
                candidate,
                events
            );

        EnrichedOfferLookupPort lookup =
            value ->
                OptionalLong.empty();

        ProductEnrichmentClient enrichmentClient =
            parsedDeal ->
                createEnrichmentResult();

        ProductPersistencePort productPort =
            parsedDeal ->
                createProduct();

        OfferSnapshotPersistencePort snapshotPort =
            snapshot ->
                new PersistedOfferSnapshot(
                    copyWithId(
                        snapshot,
                        SNAPSHOT_ID
                    ),
                    false
                );

        PaymentConditionPersistencePort paymentPort =
            (snapshotId, conditions) -> {
                throw new AssertionError(
                    "payments must not be duplicated"
                );
            };

        OfferEvidencePersistencePort evidencePort =
            (snapshotId, enrichmentResult) -> {
                throw new AssertionError(
                    "evidence must not be duplicated"
                );
            };

        RecordingQueue queue =
            new RecordingQueue(
                events
            );

        RecordingTransactionPort transactionPort =
            new RecordingTransactionPort(
                events
            );

        EnrichDealUseCase useCase =
            new EnrichDealUseCase(
                candidateRepository,
                lookup,
                enrichmentClient,
                productPort,
                new OfferSnapshotFactory(),
                snapshotPort,
                paymentPort,
                evidencePort,
                queue,
                transactionPort,
                CLOCK,
                5
            );

        long snapshotId =
            useCase.execute(
                CANDIDATE_ID
            );

        assertEquals(
            SNAPSHOT_ID,
            snapshotId
        );

        assertEquals(
            1,
            queue.submissions.size()
        );
    }

    @Test
    void shouldFailBeforeExternalCallWhenCandidateDoesNotExist() {

        DealCandidateRepositoryPort candidateRepository =
            emptyCandidateRepository();

        EnrichedOfferLookupPort lookup =
            value -> {
                throw new AssertionError(
                    "lookup must not run"
                );
            };

        ProductEnrichmentClient enrichmentClient =
            parsedDeal -> {
                throw new AssertionError(
                    "enrichment must not run"
                );
            };

        ProductPersistencePort productPort =
            parsedDeal -> {
                throw new AssertionError(
                    "product persistence must not run"
                );
            };

        OfferSnapshotPersistencePort snapshotPort =
            snapshot -> {
                throw new AssertionError(
                    "snapshot persistence must not run"
                );
            };

        PaymentConditionPersistencePort paymentPort =
            (snapshotId, conditions) -> {
                throw new AssertionError(
                    "payments must not run"
                );
            };

        OfferEvidencePersistencePort evidencePort =
            (snapshotId, result) -> {
                throw new AssertionError(
                    "evidence must not run"
                );
            };

        RecordingQueue queue =
            new RecordingQueue(
                new ArrayList<>()
            );

        RecordingTransactionPort transactionPort =
            new RecordingTransactionPort(
                new ArrayList<>()
            );

        EnrichDealUseCase useCase =
            new EnrichDealUseCase(
                candidateRepository,
                lookup,
                enrichmentClient,
                productPort,
                new OfferSnapshotFactory(),
                snapshotPort,
                paymentPort,
                evidencePort,
                queue,
                transactionPort,
                CLOCK,
                5
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                CANDIDATE_ID
            )
        );
    }

    private static DealCandidateRepositoryPort
    candidateRepositoryReturning(
        DealCandidate candidate,
        List<String> events
    ) {

        return new DealCandidateRepositoryPort() {

            @Override
            public DealCandidate save(
                DealCandidate value
            ) {

                throw new UnsupportedOperationException(
                    "save is not used by this test"
                );
            }

            @Override
            public Optional<DealCandidate> findById(
                long id
            ) {

                events.add(
                    "candidate-find"
                );

                if (candidate.id() != null
                    && candidate.id() == id) {

                    return Optional.of(
                        candidate
                    );
                }

                return Optional.empty();
            }
        };
    }

    private static DealCandidateRepositoryPort
    emptyCandidateRepository() {

        return new DealCandidateRepositoryPort() {

            @Override
            public DealCandidate save(
                DealCandidate candidate
            ) {

                throw new UnsupportedOperationException(
                    "save is not used by this test"
                );
            }

            @Override
            public Optional<DealCandidate> findById(
                long id
            ) {

                return Optional.empty();
            }
        };
    }

    private static DealCandidate createCandidate() {

        return new DealCandidate(
            CANDIDATE_ID,
            10L,
            createParsedDeal()
        );
    }

    private static ParsedDeal createParsedDeal() {

        return new ParsedDeal(
            "B087WLJH8Y",
            "https://www.amazon.com.br/dp/B087WLJH8Y",
            "Produto de teste",
            "https://example.com/image.jpg",
            new BigDecimal(
                "99.90"
            ),
            new BigDecimal(
                "129.90"
            ),
            new BigDecimal(
                "119.90"
            ),
            new BigDecimal(
                "42.00"
            ),
            4.6,
            58363L,
            NOW,
            "https://www.amazon.com.br/deals"
        );
    }

    private static ProductEnrichmentResult
    createEnrichmentResult() {

        SellerEvidence sellerEvidence =
            new SellerEvidence(
                "Amazon.com.br",
                SellerType.AMAZON,
                "merchantInfoFeature"
            );

        DeliveryEvidence deliveryEvidence =
            new DeliveryEvidence(
                "Amazon.com.br",
                DeliveryType.AMAZON,
                "fulfillerInfoFeature"
            );

        return new ProductEnrichmentResult(
            "B087WLJH8Y",
            sellerEvidence,
            deliveryEvidence,
            List.<PaymentCondition>of(),
            "test-enrichment",
            "https://www.amazon.com.br/dp/B087WLJH8Y",
            NOW
        );
    }

    private static Product createProduct() {

        return new Product(
            PRODUCT_ID,
            new Asin(
                "B087WLJH8Y"
            ),
            "Produto de teste",
            "https://example.com/image.jpg",
            "https://www.amazon.com.br/dp/B087WLJH8Y"
        );
    }

    private static OfferSnapshot copyWithId(
        OfferSnapshot snapshot,
        long id
    ) {

        return new OfferSnapshot(
            id,
            snapshot.product(),
            snapshot.collectedAt(),
            snapshot.currentPrice(),
            snapshot.basisPrice(),
            snapshot.previousPrice(),
            snapshot.soldPercentage(),
            snapshot.rating(),
            snapshot.reviewCount(),
            snapshot.sellerName(),
            snapshot.deliveryProvider(),
            snapshot.sellerType(),
            snapshot.deliveryType(),
            snapshot.source(),
            snapshot.paymentConditions()
        );
    }

    private static final class RecordingQueue
        implements ProcessingJobQueuePort {

        private final List<String> events;

        private final List<ProcessingJobSubmission>
            submissions =
            new ArrayList<>();

        private RecordingQueue(
            List<String> events
        ) {

            this.events =
                events;
        }

        @Override
        public ProcessingJob enqueue(
            ProcessingJobSubmission submission
        ) {

            events.add(
                "enqueue"
            );

            submissions.add(
                submission
            );

            return new ProcessingJob(
                1000L,
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
    }

    private static final class RecordingTransactionPort
        implements TransactionPort {

        private final List<String> events;

        private RecordingTransactionPort(
            List<String> events
        ) {

            this.events =
                events;
        }

        @Override
        public <T> T execute(
            Supplier<T> operation
        ) {

            events.add(
                "transaction-begin"
            );

            try {

                T result =
                    operation.get();

                events.add(
                    "transaction-commit"
                );

                return result;

            } catch (RuntimeException exception) {

                events.add(
                    "transaction-rollback"
                );

                throw exception;
            }
        }
    }
}
