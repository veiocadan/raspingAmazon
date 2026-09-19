package com.raspingamazon.application.deal;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.application.deal.port.OfferEvidencePersistencePort;
import com.raspingamazon.application.deal.port.OfferSnapshotPersistencePort;
import com.raspingamazon.application.deal.port.PaymentConditionPersistencePort;
import com.raspingamazon.application.deal.port.ProductPersistencePort;
import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.commercial.PaymentCondition;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testa a orquestração completa da camada de aplicação.
 *
 * <p>As dependências são fakes em memória. O objetivo não é testar
 * PostgreSQL ou HTTP, mas provar a ordem e o transporte de dados
 * através do fluxo vertical.</p>
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
    void shouldExecuteCompleteSynchronousFlow() {

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
                        clock
                );

        CollectionRequest request =
                new CollectionRequest(
                        URI.create(
                                "https://www.amazon.com.br/deals"
                        )
                );

        List<ProcessedDealResult> results =
                service.process(
                        request
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
         * Prova que os campos comerciais fechados na D2/D3
         * atravessaram todo o pipeline.
         */
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
                        "product",
                        "snapshot",
                        "payments",
                        "evidence",
                        "evaluation"
                ),
                events
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

    /**
     * Simula o comportamento esperado de um adapter de persistência:
     * recebe snapshot sem id e devolve representação persistida com id.
     */
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
     * Fake que apenas registra que a etapa de avaliação foi executada.
     */
    private static final class RecordingEvaluationPort
            implements DealEvaluationProcessingPort {

        private final List<String> events;

        private OfferSnapshot snapshot;
        private OffsetDateTime evaluatedAt;

        private RecordingEvaluationPort(
                List<String> events
        ) {
            this.events = events;
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
}