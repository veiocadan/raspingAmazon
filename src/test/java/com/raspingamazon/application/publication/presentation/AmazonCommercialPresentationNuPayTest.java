package com.raspingamazon.application.publication.presentation;

import com.raspingamazon.application.publication.PublicationData;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Compatibilidade da apresentação comercial com NuPay genérico.
 */
class AmazonCommercialPresentationNuPayTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-23T15:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-23T15:05:00-03:00"
        );

    @Test
    void shouldPresentGenericNuPayWhenItIsTheOnlyCashMethod() {

        PaymentCondition nuPay =
            new PaymentCondition(
                PaymentConditionType.CASH,
                Money.of(
                    "89.90"
                ),
                Percentage.of(
                    "10"
                ),
                null,
                null,
                null,
                null,
                List.of(
                    PaymentMethod.NUPAY
                )
            );

        AmazonCommercialPresentationV1 policy =
            new AmazonCommercialPresentationV1();

        CommercialPresentation presentation =
            policy.present(
                createPublicationData(
                    List.of(
                        nuPay
                    )
                )
            );

        assertEquals(
            PaymentMethod.NUPAY,
            presentation.primaryCashCondition()
                .paymentMethod()
        );

        assertSame(
            nuPay,
            presentation.primaryCashCondition()
                .condition()
        );

        assertNull(
            presentation.secondaryCashCondition()
        );
    }

    private PublicationData createPublicationData(
        List<PaymentCondition> paymentConditions
    ) {

        Product product =
            new Product(
                10L,
                new Asin(
                    "B0NUPAY001"
                ),
                "Produto para teste NuPay",
                null,
                "https://www.amazon.com.br/dp/B0NUPAY001"
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                20L,
                product,
                COLLECTED_AT,
                Money.of(
                    "99.90"
                ),
                Money.of(
                    "129.90"
                ),
                null,
                Percentage.of(
                    "42"
                ),
                4.8,
                1500L,
                "Amazon.com.br",
                "Amazon.com.br",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "TEST_NUPAY",
                paymentConditions
            );

        DealEvaluation evaluation =
            new DealEvaluation(
                30L,
                snapshot,
                true,
                null,
                "AMAZON_ELIGIBILITY_TEST",
                "COMMERCIAL_FILTER_TEST",
                List.of(
                    EvaluationRuleResult.passed(
                        "SELLER_IS_AMAZON",
                        "AMAZON",
                        "AMAZON"
                    )
                ),
                null,
                null,
                null,
                null,
                EVALUATED_AT
            );

        return new PublicationData(
            evaluation
        );
    }
}
