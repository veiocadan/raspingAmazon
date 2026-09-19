package com.raspingamazon.application.deal;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.application.deal.port.OfferEvidencePersistencePort;
import com.raspingamazon.application.deal.port.OfferSnapshotPersistencePort;
import com.raspingamazon.application.deal.port.PaymentConditionPersistencePort;
import com.raspingamazon.application.deal.port.ProductPersistencePort;
import com.raspingamazon.application.deal.port.TransactionPort;
import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa a orquestração vertical da camada de aplicação.
 */
class AmazonDealProcessingServiceTest {

    private static final String ASIN =
            "B087WLJH8Y";

    private static final OffsetDateTime COLLECTED_AT =
            OffsetDateTime.parse(
                    "2026-09-18T20:00:00Z"
            );

    private static final OffsetDateTime ENRICHED_AT =
            OffsetDateTime.parse(
                    "2026-09-18T20:01:00Z"
            );

    private static final Instant EVALUATED_INSTANT =
            Instant.parse(
                    "2026-09-18T20:02:00Z"
            );

    @Test
    void shouldExecutePersistenceOnlyInsideTransaction() {

        List<String> events =
                new ArrayList<>();

        ParsedDeal parsedDeal =
                createParsedDeal();

        CollectionCollector collector =
                request -> {

                    events.add(
                            "collect"
                    );

                    return new CollectionResult(
                            "conteudo-controlado-pelo-teste",
                            COLLECTED_AT,
                            request.source().toString()
                    );
                };

        DealsParser parser =
                collectionResult -> {

                    events.add(
                            "parse"
                    );

                    return List.of(
                            parsedDeal
                    );
                };

        ProductEnrichmentClient enrichmentClient =
                deal -> {

                    events.add(
                            "enrich"
                    );

                    return createEnrichmentResult(
                            deal.asin()
                    );
                };

        ProductPersistencePort productPersistencePort =
                deal -> {

                    events.add(
                            "product"
                    );

                    return createProduct(
                            deal,
                            100L
                    );
                };

        OfferSnapshotPersistencePort snapshotPersistencePort =
                snapshot -> {

                    events.add(
                            "snapshot"
                    );

                    return new PersistedOfferSnapshot(
                            copyWithId(
                                    snapshot,
                                    200L
                            ),
                            true
                    );
                };

        PaymentConditionPersistencePort paymentPersistencePort =
                (
                        snapshotId,
                        conditions
                ) -> {

                    events.add(
                            "payments"
                    );

                    assertEquals(
                            200L,
                            snapshotId
                    );

                    assertTrue(
                            conditions.isEmpty()
                    );
                };

        OfferEvidencePersistencePort evidencePersistencePort =
                (
                        snapshotId,
                        enrichmentResult
                ) -> {

                    events.add(
                            "evidence"
                    );

                    assertEquals(
                            200L,
                            snapshotId
                    );

                    assertEquals(
                            ASIN,
                            enrichmentResult.asin()
                    );
                };

        RecordingEvaluationPort evaluationPort =
                new RecordingEvaluationPort(
                        events
                );

        RecordingTransactionPort transactionPort =
                new RecordingTransactionPort(
                        events
                );

        AmazonDealProcessingService service =
                createService(
                        collector,
                        parser,
                        enrichmentClient,
                        productPersistencePort,
                        snapshotPersistencePort,
                        paymentPersistencePort,
                        evidencePersistencePort,
                        evaluationPort,
                        transactionPort
                );

        List<ProcessedDealResult> results =
                service.process(
                        createRequest()
                );

        assertEquals(
                1,
                results.size()
        );

        ProcessedDealResult result =
                results.getFirst();

        assertSame(
                parsedDeal,
                result.parsedDeal()
        );

        assertEquals(
                100L,
                result.product().id()
        );

        assertEquals(
                200L,
                result.offerSnapshot().id()
        );

        assertEquals(
                4.6,
                result.offerSnapshot().rating()
        );

        assertEquals(
                58363L,
                result.offerSnapshot().reviewCount()
        );

        assertEquals(
                List.of(
                        "collect",
                        "parse",
                        "enrich",
                        "transaction-begin",
                        "product",
                        "snapshot",
                        "payments",
                        "evidence",
                        "evaluation",
                        "transaction-commit"
                ),
                events
        );

        assertEquals(
                1,
                transactionPort.executions
        );

        assertNotNull(
                evaluationPort.snapshot
        );

        assertEquals(
                200L,
                evaluationPort.snapshot.id()
        );
    }

    @Test
    void shouldNotOpenTransactionWhenEnrichmentFails() {

        List<String> events =
                new ArrayList<>();

        ParsedDeal parsedDeal =
                createParsedDeal();

        CollectionCollector collector =
                request -> {

                    events.add(
                            "collect"
                    );

                    return new CollectionResult(
                            "conteudo-controlado-pelo-teste",
                            COLLECTED_AT,
                            request.source().toString()
                    );
                };

        DealsParser parser =
                collectionResult -> {

                    events.add(
                            "parse"
                    );

                    return List.of(
                            parsedDeal
                    );
                };

        ProductEnrichmentClient failingEnrichmentClient =
                deal -> {

                    events.add(
                            "enrich"
                    );

                    throw new IllegalStateException(
                            "controlled enrichment failure"
                    );
                };

        RecordingTransactionPort transactionPort =
                new RecordingTransactionPort(
                        events
                );

        ProductPersistencePort productPersistencePort =
                deal -> {
                    throw new AssertionError(
                            "Product persistence must not execute"
                    );
                };

        OfferSnapshotPersistencePort snapshotPersistencePort =
                snapshot -> {
                    throw new AssertionError(
                            "Snapshot persistence must not execute"
                    );
                };

        PaymentConditionPersistencePort paymentPersistencePort =
                (
                        snapshotId,
                        conditions
                ) -> {
                    throw new AssertionError(
                            "Payment persistence must not execute"
                    );
                };

        OfferEvidencePersistencePort evidencePersistencePort =
                (
                        snapshotId,
                        enrichmentResult
                ) -> {
                    throw new AssertionError(
                            "Evidence persistence must not execute"
                    );
                };

        DealEvaluationProcessingPort evaluationPort =
                (
                        snapshot,
                        evaluatedAt
                ) -> {
                    throw new AssertionError(
                            "Evaluation must not execute"
                    );
                };

        AmazonDealProcessingService service =
                createService(
                        collector,
                        parser,
                        failingEnrichmentClient,
                        productPersistencePort,
                        snapshotPersistencePort,
                        paymentPersistencePort,
                        evidencePersistencePort,
                        evaluationPort,
                        transactionPort
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.process(
                        createRequest()
                )
        );

        assertEquals(
                List.of(
                        "collect",
                        "parse",
                        "enrich"
                ),
                events
        );

        assertEquals(
                0,
                transactionPort.executions
        );
    }

    @Test
    void shouldNotDuplicateDependentPersistenceWhenSnapshotAlreadyExists() {

        AtomicInteger paymentExecutions =
                new AtomicInteger();

        AtomicInteger evidenceExecutions =
                new AtomicInteger();

        AtomicInteger evaluationExecutions =
                new AtomicInteger();

        AtomicBoolean snapshotAlreadyCreated =
                new AtomicBoolean();

        ParsedDeal parsedDeal =
                createParsedDeal();

        CollectionCollector collector =
                request -> new CollectionResult(
                        "conteudo-controlado-pelo-teste",
                        COLLECTED_AT,
                        request.source().toString()
                );

        DealsParser parser =
                collectionResult -> List.of(
                        parsedDeal
                );

        ProductEnrichmentClient enrichmentClient =
                deal -> createEnrichmentResult(
                        deal.asin()
                );

        ProductPersistencePort productPersistencePort =
                deal -> createProduct(
                        deal,
                        100L
                );

        OfferSnapshotPersistencePort snapshotPersistencePort =
                snapshot -> {

                    boolean created =
                            !snapshotAlreadyCreated.getAndSet(
                                    true
                            );

                    return new PersistedOfferSnapshot(
                            copyWithId(
                                    snapshot,
                                    200L
                            ),
                            created
                    );
                };

        PaymentConditionPersistencePort paymentPersistencePort =
                (
                        snapshotId,
                        conditions
                ) -> paymentExecutions.incrementAndGet();

        OfferEvidencePersistencePort evidencePersistencePort =
                (
                        snapshotId,
                        enrichmentResult
                ) -> evidenceExecutions.incrementAndGet();

        DealEvaluationProcessingPort evaluationPort =
                (
                        snapshot,
                        evaluatedAt
                ) -> evaluationExecutions.incrementAndGet();

        /*
         * TransactionPort possui um método genérico.
         *
         * Por isso usamos classe anônima em vez de lambda.
         * Uma lambda não consegue satisfazer diretamente esse
         * functional descriptor genérico.
         */
        TransactionPort transactionPort =
                new TransactionPort() {

                    @Override
                    public <T> T execute(
                            Supplier<T> operation
                    ) {
                        return operation.get();
                    }
                };

        AmazonDealProcessingService service =
                createService(
                        collector,
                        parser,
                        enrichmentClient,
                        productPersistencePort,
                        snapshotPersistencePort,
                        paymentPersistencePort,
                        evidencePersistencePort,
                        evaluationPort,
                        transactionPort
                );

        /*
         * Primeira execução:
         * snapshot created=true.
         */
        List<ProcessedDealResult> first =
                service.process(
                        createRequest()
                );

        /*
         * Segunda execução da mesma observação:
         * snapshot created=false.
         */
        List<ProcessedDealResult> second =
                service.process(
                        createRequest()
                );

        assertEquals(
                1,
                first.size()
        );

        assertEquals(
                1,
                second.size()
        );

        assertEquals(
                200L,
                first.getFirst()
                        .offerSnapshot()
                        .id()
        );

        assertEquals(
                200L,
                second.getFirst()
                        .offerSnapshot()
                        .id()
        );

        /*
         * Dependências do snapshot devem ser persistidas
         * uma única vez.
         */
        assertEquals(
                1,
                paymentExecutions.get()
        );

        assertEquals(
                1,
                evidenceExecutions.get()
        );

        assertEquals(
                1,
                evaluationExecutions.get()
        );
    }

    private AmazonDealProcessingService createService(
            CollectionCollector collector,
            DealsParser parser,
            ProductEnrichmentClient enrichmentClient,
            ProductPersistencePort productPersistencePort,
            OfferSnapshotPersistencePort snapshotPersistencePort,
            PaymentConditionPersistencePort paymentPersistencePort,
            OfferEvidencePersistencePort evidencePersistencePort,
            DealEvaluationProcessingPort evaluationPort,
            TransactionPort transactionPort
    ) {
        return new AmazonDealProcessingService(
                collector,
                parser,
                enrichmentClient,
                productPersistencePort,
                new OfferSnapshotFactory(),
                snapshotPersistencePort,
                paymentPersistencePort,
                evidencePersistencePort,
                evaluationPort,
                transactionPort,
                Clock.fixed(
                        EVALUATED_INSTANT,
                        ZoneOffset.UTC
                )
        );
    }

    private CollectionRequest createRequest() {

        return new CollectionRequest(
                URI.create(
                        "https://www.amazon.com.br/deals"
                )
        );
    }

    private ParsedDeal createParsedDeal() {

        return new ParsedDeal(
                ASIN,
                "https://www.amazon.com.br/dp/" + ASIN,
                "Creatina de teste",
                "https://example.com/image.jpg",
                new BigDecimal("15.99"),
                new BigDecimal("53.46"),
                null,
                new BigDecimal("89"),
                4.6,
                58363L,
                COLLECTED_AT,
                "https://www.amazon.com.br/deals"
        );
    }

    private ProductEnrichmentResult createEnrichmentResult(
            String asin
    ) {

        return new ProductEnrichmentResult(
                asin,

                new SellerEvidence(
                        "Amazon.com.br",
                        SellerType.AMAZON,
                        "merchantInfoFeature"
                ),

                new DeliveryEvidence(
                        "Amazon.com.br",
                        DeliveryType.AMAZON,
                        "fulfillerInfoFeature"
                ),

                "AMAZON_PRODUCT_PAGE",

                "https://www.amazon.com.br/dp/" + asin,

                ENRICHED_AT
        );
    }

    private Product createProduct(
            ParsedDeal deal,
            long id
    ) {
        return new Product(
                id,
                new Asin(
                        deal.asin()
                ),
                deal.title(),
                deal.imageUrl(),
                deal.productUrl()
        );
    }

    private OfferSnapshot copyWithId(
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

    private static final class RecordingEvaluationPort
            implements DealEvaluationProcessingPort {

        private final List<String> events;

        private OfferSnapshot snapshot;

        private OffsetDateTime evaluatedAt;

        private RecordingEvaluationPort(
                List<String> events
        ) {
            this.events =
                    events;
        }

        @Override
        public void evaluateAndPersist(
                OfferSnapshot offerSnapshot,
                OffsetDateTime evaluatedAt
        ) {

            events.add(
                    "evaluation"
            );

            this.snapshot =
                    offerSnapshot;

            this.evaluatedAt =
                    evaluatedAt;
        }
    }

    private static final class RecordingTransactionPort
            implements TransactionPort {

        private final List<String> events;

        private int executions;

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

            executions++;

            events.add(
                    "transaction-begin"
            );

            T result =
                    operation.get();

            events.add(
                    "transaction-commit"
            );

            return result;
        }
    }
}