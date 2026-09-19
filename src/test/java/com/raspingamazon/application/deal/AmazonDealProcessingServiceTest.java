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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa a orquestração vertical da camada de aplicação.
 *
 * <p>Este teste não utiliza JDBC ou HTTP real. As dependências em
 * memória permitem observar exatamente em qual ordem cada fronteira
 * da aplicação é executada.</p>
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

                    return new Product(
                            100L,
                            new Asin(
                                    deal.asin()
                            ),
                            deal.title(),
                            deal.imageUrl(),
                            deal.productUrl()
                    );
                };

        OfferSnapshotPersistencePort snapshotPersistencePort =
                snapshot -> {

                    events.add(
                            "snapshot"
                    );

                    return copyWithId(
                            snapshot,
                            200L
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

        Clock clock =
                Clock.fixed(
                        EVALUATED_INSTANT,
                        ZoneOffset.UTC
                );

        AmazonDealProcessingService service =
                new AmazonDealProcessingService(
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
                        clock
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

        /*
         * Os campos comerciais continuam chegando ao snapshot.
         */
        assertEquals(
                4.6,
                result.offerSnapshot().rating()
        );

        assertEquals(
                58363L,
                result.offerSnapshot().reviewCount()
        );

        /*
         * A propriedade principal da F2:
         *
         * enrichment ocorre ANTES de transaction-begin.
         */
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

        assertEquals(
                OffsetDateTime.ofInstant(
                        EVALUATED_INSTANT,
                        ZoneOffset.UTC
                ),
                evaluationPort.evaluatedAt
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

        /*
         * As dependências abaixo não devem ser alcançadas.
         * Caso alguma delas seja executada, o próprio teste falha.
         */
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
                new AmazonDealProcessingService(
                        collector,
                        parser,
                        failingEnrichmentClient,
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

        assertThrows(
                IllegalStateException.class,
                () -> service.process(
                        createRequest()
                )
        );

        /*
         * Como enrichment falhou antes da fronteira de escrita,
         * a transação jamais deve ter sido aberta.
         */
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

    /**
     * Fake da etapa de avaliação.
     */
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

    /**
     * Fake utilizado para observar quando a fronteira transacional
     * começa e termina.
     */
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