package com.raspingamazon.application.evaluation;

import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.application.filter.FilterProfileProvider;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.filter.CommercialFilterEngine;
import com.raspingamazon.domain.filter.FilterProfile;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica que AmazonDealEvaluationApplicationService pode ser usado
 * diretamente como porta de avaliação pelo fluxo vertical.
 *
 * A partir da FASE 9, a avaliação aplicada pela porta contém:
 *
 * - elegibilidade estrutural Amazon;
 * - filtros comerciais configuráveis.
 */
class AmazonDealEvaluationProcessingPortTest {

    private static final FilterProfile FILTER_PROFILE =
        new FilterProfile(
            "COMMERCIAL_FILTER_V1",
            Percentage.of("20"),
            new BigDecimal("4.3"),
            100L
        );

    private static final FilterProfileProvider FILTER_PROFILE_PROVIDER =
        () -> FILTER_PROFILE;

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
                new CommercialFilterEngine(),
                FILTER_PROFILE_PROVIDER,
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

        assertNull(
            evaluation.rejectionReason()
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
            "COMMERCIAL_FILTER_V1",
            evaluation.filterProfileVersion()
        );

        assertEquals(
            evaluatedAt,
            evaluation.evaluatedAt()
        );

        /*
         * A avaliação atual possui cinco regras:
         *
         * 1. seller;
         * 2. delivery;
         * 3. desconto à vista;
         * 4. rating;
         * 5. reviews.
         */
        assertEquals(
            5,
            evaluation.ruleResults().size()
        );

        assertTrue(
            evaluation.ruleResults()
                .stream()
                .allMatch(
                    result ->
                        result.passed()
                )
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

        PaymentCondition cashCondition =
            new PaymentCondition(
                PaymentConditionType.CASH,
                Money.of(
                    "79.90"
                ),
                Percentage.of(
                    "25"
                ),
                null,
                null,
                null,
                null,
                List.of(
                    PaymentMethod.PIX,
                    PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                )
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
            4.7,
            1500L,
            "Amazon.com.br",
            "Amazon.com.br",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "https://www.amazon.com.br/deals",
            List.of(
                cashCondition
            )
        );
    }
}
