package com.raspingamazon.application.evaluation;

import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que AmazonDealEvaluationApplicationService pode ser usado
 * diretamente como porta de avaliação pelo fluxo vertical.
 */
class AmazonDealEvaluationProcessingPortTest {

    @Test
    void shouldEvaluateSnapshotThroughProcessingPort() {

        AtomicReference<DealEvaluation> persisted =
                new AtomicReference<>();

        DealEvaluationRepository repository =
                evaluation -> {

                    persisted.set(
                            evaluation
                    );

                    return evaluation;
                };

        AmazonDealEvaluationApplicationService service =
                new AmazonDealEvaluationApplicationService(
                        new AmazonEligibilityValidator(),
                        repository
                );

        /*
         * O tipo estático abaixo é a porta consumida pelo
         * AmazonDealProcessingService.
         */
        DealEvaluationProcessingPort port =
                service;

        OfferSnapshot snapshot =
                createAmazonSnapshot();

        OffsetDateTime evaluatedAt =
                OffsetDateTime.parse(
                        "2026-09-18T20:02:00Z"
                );

        port.evaluateAndPersist(
                snapshot,
                evaluatedAt
        );

        DealEvaluation evaluation =
                persisted.get();

        assertNotNull(
                evaluation
        );

        assertTrue(
                evaluation.eligible()
        );

        assertEquals(
                snapshot,
                evaluation.offerSnapshot()
        );

        assertEquals(
                "AMAZON_SELLER_DELIVERY_V1",
                evaluation.eligibilityPolicyVersion()
        );

        assertEquals(
                evaluatedAt,
                evaluation.evaluatedAt()
        );

        /*
         * A política atual possui pelo menos as regras de
         * seller e delivery, ambas preservadas individualmente.
         */
        assertEquals(
                2,
                evaluation.ruleResults().size()
        );
    }

    private OfferSnapshot createAmazonSnapshot() {

        Product product =
                new Product(
                        100L,
                        new Asin(
                                "B087WLJH8Y"
                        ),
                        "Produto de teste",
                        null,
                        "https://www.amazon.com.br/dp/B087WLJH8Y"
                );

        return new OfferSnapshot(
                200L,
                product,
                OffsetDateTime.parse(
                        "2026-09-18T20:00:00Z"
                ),
                Money.of(
                        "99.90"
                ),
                null,
                null,
                null,
                null,
                null,
                "Amazon.com.br",
                "Amazon.com.br",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "https://www.amazon.com.br/deals",
                List.of()
        );
    }
}