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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommercialFilterEngineTest {

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

    private final CommercialFilterEngine engine =
        new CommercialFilterEngine();

    @Test
    void shouldPassAllCommercialFilters() {

        List<EvaluationRuleResult> results =
            engine.evaluate(
                snapshot(
                    4.7,
                    1500L,
                    List.of(
                        cashCondition(
                            "25",
                            PaymentMethod.PIX
                        )
                    )
                ),
                PROFILE
            );

        assertEquals(
            3,
            results.size()
        );

        assertTrue(
            results.stream()
                .allMatch(
                    EvaluationRuleResult::passed
                )
        );
    }

    @Test
    void shouldPreserveStableRuleOrder() {

        List<EvaluationRuleResult> results =
            engine.evaluate(
                snapshot(
                    4.7,
                    1500L,
                    List.of(
                        cashCondition(
                            "25",
                            PaymentMethod.PIX
                        )
                    )
                ),
                PROFILE
            );

        assertEquals(
            "MIN_CASH_DISCOUNT",
            results.get(0).ruleCode()
        );

        assertEquals(
            "MIN_RATING",
            results.get(1).ruleCode()
        );

        assertEquals(
            "MIN_REVIEW_COUNT",
            results.get(2).ruleCode()
        );
    }

    @Test
    void shouldEvaluateAllRulesWhenAllFiltersFail() {

        List<EvaluationRuleResult> results =
            engine.evaluate(
                snapshot(
                    4.0,
                    50L,
                    List.of(
                        cashCondition(
                            "10",
                            PaymentMethod.PIX
                        )
                    )
                ),
                PROFILE
            );

        assertEquals(
            3,
            results.size()
        );

        assertFalse(
            results.get(0).passed()
        );

        assertEquals(
            RejectionReason.CASH_DISCOUNT_BELOW_MINIMUM,
            results.get(0).reasonCode()
        );

        assertFalse(
            results.get(1).passed()
        );

        assertEquals(
            RejectionReason.RATING_BELOW_MINIMUM,
            results.get(1).reasonCode()
        );

        assertFalse(
            results.get(2).passed()
        );

        assertEquals(
            RejectionReason.REVIEW_COUNT_BELOW_MINIMUM,
            results.get(2).reasonCode()
        );
    }

    @Test
    void shouldDistinguishUnavailableDataAcrossRules() {

        List<EvaluationRuleResult> results =
            engine.evaluate(
                snapshot(
                    null,
                    null,
                    List.of()
                ),
                PROFILE
            );

        assertEquals(
            RejectionReason.CASH_DISCOUNT_UNAVAILABLE,
            results.get(0).reasonCode()
        );

        assertEquals(
            RejectionReason.RATING_UNAVAILABLE,
            results.get(1).reasonCode()
        );

        assertEquals(
            RejectionReason.REVIEW_COUNT_UNAVAILABLE,
            results.get(2).reasonCode()
        );
    }

    @Test
    void shouldProduceSameResultsForSameSnapshotAndProfile() {

        OfferSnapshot snapshot =
            snapshot(
                4.8,
                2500L,
                List.of(
                    cashCondition(
                        "22",
                        PaymentMethod.PIX
                    ),
                    cashCondition(
                        "25",
                        PaymentMethod.NUPAY_ADDITIONAL_LIMIT
                    )
                )
            );

        List<EvaluationRuleResult> first =
            engine.evaluate(
                snapshot,
                PROFILE
            );

        List<EvaluationRuleResult> second =
            engine.evaluate(
                snapshot,
                PROFILE
            );

        assertEquals(
            first,
            second
        );
    }

    @Test
    void shouldReturnImmutableResultList() {

        List<EvaluationRuleResult> results =
            engine.evaluate(
                snapshot(
                    4.7,
                    1500L,
                    List.of(
                        cashCondition(
                            "25",
                            PaymentMethod.PIX
                        )
                    )
                ),
                PROFILE
            );

        assertThrows(
            UnsupportedOperationException.class,
            () -> results.add(
                results.get(0)
            )
        );
    }

    @Test
    void shouldRejectNullSnapshot() {

        assertThrows(
            NullPointerException.class,
            () -> engine.evaluate(
                null,
                PROFILE
            )
        );
    }

    @Test
    void shouldRejectNullProfile() {

        assertThrows(
            NullPointerException.class,
            () -> engine.evaluate(
                snapshot(
                    4.7,
                    1500L,
                    List.of(
                        cashCondition(
                            "25",
                            PaymentMethod.PIX
                        )
                    )
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

    private OfferSnapshot snapshot(
        Double rating,
        Long reviewCount,
        List<PaymentCondition> paymentConditions
    ) {
        return new OfferSnapshot(
            null,
            PRODUCT,
            OffsetDateTime.parse(
                "2026-09-20T14:35:00-03:00"
            ),
            Money.of("199.90"),
            null,
            null,
            null,
            rating,
            reviewCount,
            "Amazon.com.br",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "amazon-deals",
            paymentConditions
        );
    }
}
