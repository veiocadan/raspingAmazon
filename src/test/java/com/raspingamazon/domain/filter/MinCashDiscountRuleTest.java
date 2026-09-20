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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinCashDiscountRuleTest {

    private static final Product PRODUCT =
        new Product(
            1L,
            new Asin("B0FN4BK3V7"),
            "Produto de teste",
            null,
            "https://www.amazon.com.br/dp/B0FN4BK3V7"
        );

    private static final FilterProfile PROFILE =
        new FilterProfile(
            "COMMERCIAL_FILTER_V1",
            Percentage.of("20"),
            new BigDecimal("4.3"),
            100L
        );

    private final MinCashDiscountRule rule =
        new MinCashDiscountRule();

    @Test
    void shouldPassWhenNuPayHasDiscountAboveMinimum() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithConditions(
                    List.of(
                        cashCondition(
                            "10",
                            PaymentMethod.PIX
                        ),
                        cashCondition(
                            "25",
                            PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                        )
                    )
                ),
                PROFILE
            );

        assertTrue(
            result.passed()
        );

        assertEquals(
            "MIN_CASH_DISCOUNT",
            result.ruleCode()
        );

        assertEquals(
            "25|NUPAY_ADDITIONAL_LIMIT",
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
    void shouldPassWhenDiscountEqualsMinimum() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithConditions(
                    List.of(
                        cashCondition(
                            "20",
                            PaymentMethod.PIX
                        )
                    )
                ),
                PROFILE
            );

        assertTrue(
            result.passed()
        );

        assertEquals(
            "20|PIX",
            result.observedValue()
        );
    }

    @Test
    void shouldFailWhenBestCashDiscountIsBelowMinimum() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithConditions(
                    List.of(
                        cashCondition(
                            "8",
                            PaymentMethod.PIX
                        ),
                        cashCondition(
                            "12",
                            PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                        )
                    )
                ),
                PROFILE
            );

        assertFalse(
            result.passed()
        );

        assertEquals(
            "12|NUPAY_ADDITIONAL_LIMIT",
            result.observedValue()
        );

        assertEquals(
            "20",
            result.threshold()
        );

        assertEquals(
            RejectionReason.CASH_DISCOUNT_BELOW_MINIMUM,
            result.reasonCode()
        );
    }

    @Test
    void shouldFailAsUnavailableWhenNoCashDiscountExists() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithConditions(
                    List.of()
                ),
                PROFILE
            );

        assertFalse(
            result.passed()
        );

        assertEquals(
            "UNAVAILABLE",
            result.observedValue()
        );

        assertEquals(
            "20",
            result.threshold()
        );

        assertEquals(
            RejectionReason.CASH_DISCOUNT_UNAVAILABLE,
            result.reasonCode()
        );
    }

    @Test
    void shouldIgnoreCreditCardDiscountForCashFilter() {

        PaymentCondition creditCondition =
            new PaymentCondition(
                PaymentConditionType.CREDIT_INSTALLMENT,
                null,
                Percentage.of("30"),
                10,
                Money.of("100.00"),
                Money.of("1000.00"),
                Percentage.of("0"),
                List.of(
                    PaymentMethod.CREDIT_CARD
                )
            );

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithConditions(
                    List.of(
                        creditCondition
                    )
                ),
                PROFILE
            );

        assertFalse(
            result.passed()
        );

        assertEquals(
            RejectionReason.CASH_DISCOUNT_UNAVAILABLE,
            result.reasonCode()
        );
    }

    @Test
    void shouldRejectNullSnapshot() {

        assertThrows(
            NullPointerException.class,
            () -> rule.evaluate(
                null,
                PROFILE
            )
        );
    }

    @Test
    void shouldRejectNullProfile() {

        assertThrows(
            NullPointerException.class,
            () -> rule.evaluate(
                snapshotWithConditions(
                    List.of()
                ),
                null
            )
        );
    }

    private PaymentCondition cashCondition(
        String discount,
        PaymentMethod paymentMethod
    ) {
        return new PaymentCondition(
            PaymentConditionType.CASH,
            null,
            Percentage.of(discount),
            null,
            null,
            null,
            null,
            List.of(paymentMethod)
        );
    }

    private OfferSnapshot snapshotWithConditions(
        List<PaymentCondition> conditions
    ) {
        return new OfferSnapshot(
            null,
            PRODUCT,
            OffsetDateTime.parse(
                "2026-09-20T14:30:00-03:00"
            ),
            Money.of("199.90"),
            null,
            null,
            null,
            4.7,
            1500L,
            "Amazon.com.br",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "amazon-deals",
            conditions
        );
    }
}
