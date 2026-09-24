package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinBasisDiscountRuleTest {

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-23T21:00:00-03:00"
        );

    private final MinBasisDiscountRule rule =
        new MinBasisDiscountRule();

    @Test
    void shouldPassGalaxyExampleUsingCashPriceAgainstBasis() {

        OfferSnapshot snapshot =
            snapshot(
                Money.of(
                    "1898.00"
                ),
                Money.of(
                    "3599.00"
                ),
                List.of(
                    cashCondition(
                        "1708.20"
                    )
                )
            );

        EvaluationRuleResult result =
            rule.evaluate(
                snapshot,
                Percentage.of(
                    "20"
                )
            );

        assertTrue(
            result.passed()
        );

        assertEquals(
            "MIN_BASIS_DISCOUNT",
            result.ruleCode()
        );

        assertEquals(
            "DISCOUNT=52.5368|BASIS=3599.00|EFFECTIVE=1708.20|SOURCE=CASH_CONDITION",
            result.observedValue()
        );

        assertEquals(
            "20",
            result.threshold()
        );

        assertNull(
            result.reasonCode()
        );
    }

    @Test
    void shouldPassWithoutCashConditionWhenCurrentPriceHasEnoughBasisDiscount() {

        OfferSnapshot snapshot =
            snapshot(
                Money.of(
                    "3298.99"
                ),
                Money.of(
                    "5499.00"
                ),
                List.of()
            );

        EvaluationRuleResult result =
            rule.evaluate(
                snapshot,
                Percentage.of(
                    "20"
                )
            );

        assertTrue(
            result.passed()
        );

        assertEquals(
            "DISCOUNT=40.0075|BASIS=5499.00|EFFECTIVE=3298.99|SOURCE=CURRENT_PRICE",
            result.observedValue()
        );
    }

    @Test
    void shouldFailWhenBasisDiscountIsBelowMinimum() {

        OfferSnapshot snapshot =
            snapshot(
                Money.of(
                    "4499.00"
                ),
                Money.of(
                    "5299.99"
                ),
                List.of()
            );

        EvaluationRuleResult result =
            rule.evaluate(
                snapshot,
                Percentage.of(
                    "20"
                )
            );

        assertTrue(
            !result.passed()
        );

        assertEquals(
            "DISCOUNT=15.1130|BASIS=5299.99|EFFECTIVE=4499.00|SOURCE=CURRENT_PRICE",
            result.observedValue()
        );

        assertEquals(
            RejectionReason.BASIS_DISCOUNT_BELOW_MINIMUM,
            result.reasonCode()
        );
    }

    @Test
    void shouldFailAsUnavailableWhenBasisPriceIsMissing() {

        OfferSnapshot snapshot =
            snapshot(
                Money.of(
                    "100.00"
                ),
                null,
                List.of()
            );

        EvaluationRuleResult result =
            rule.evaluate(
                snapshot,
                Percentage.of(
                    "20"
                )
            );

        assertTrue(
            !result.passed()
        );

        assertEquals(
            "UNAVAILABLE",
            result.observedValue()
        );

        assertEquals(
            RejectionReason.BASIS_DISCOUNT_UNAVAILABLE,
            result.reasonCode()
        );
    }

    private PaymentCondition cashCondition(
        String price
    ) {

        return new PaymentCondition(
            PaymentConditionType.CASH,
            Money.of(
                price
            ),
            Percentage.of(
                "10"
            ),
            null,
            null,
            null,
            null,
            List.of(
                PaymentMethod.PIX
            )
        );
    }

    private OfferSnapshot snapshot(
        Money currentPrice,
        Money basisPrice,
        List<PaymentCondition> paymentConditions
    ) {

        Product product =
            new Product(
                1L,
                new Asin(
                    "B0BASIS001"
                ),
                "Produto de teste",
                null,
                "https://www.amazon.com.br/dp/B0BASIS001"
            );

        return new OfferSnapshot(
            10L,
            product,
            COLLECTED_AT,
            currentPrice,
            basisPrice,
            null,
            null,
            4.8,
            500L,
            "Amazon.com.br",
            "Amazon.com.br",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "TEST",
            paymentConditions
        );
    }
}
