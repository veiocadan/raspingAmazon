package com.raspingamazon.domain.filter;

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

class MinReviewCountRuleTest {

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

    private final MinReviewCountRule rule =
        new MinReviewCountRule();

    @Test
    void shouldPassWhenReviewCountIsAboveMinimum() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithReviewCount(1500L),
                PROFILE
            );

        assertTrue(
            result.passed()
        );

        assertEquals(
            "MIN_REVIEW_COUNT",
            result.ruleCode()
        );

        assertEquals(
            "1500",
            result.observedValue()
        );

        assertEquals(
            "100",
            result.threshold()
        );

        assertNull(
            result.reasonCode()
        );
    }

    @Test
    void shouldPassWhenReviewCountEqualsMinimum() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithReviewCount(100L),
                PROFILE
            );

        assertTrue(
            result.passed()
        );

        assertEquals(
            "100",
            result.observedValue()
        );

        assertEquals(
            "100",
            result.threshold()
        );
    }

    @Test
    void shouldFailWhenReviewCountIsBelowMinimum() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithReviewCount(99L),
                PROFILE
            );

        assertFalse(
            result.passed()
        );

        assertEquals(
            "MIN_REVIEW_COUNT",
            result.ruleCode()
        );

        assertEquals(
            "99",
            result.observedValue()
        );

        assertEquals(
            "100",
            result.threshold()
        );

        assertEquals(
            RejectionReason.REVIEW_COUNT_BELOW_MINIMUM,
            result.reasonCode()
        );
    }

    @Test
    void shouldFailAsUnavailableWhenReviewCountIsNull() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithReviewCount(null),
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
            "100",
            result.threshold()
        );

        assertEquals(
            RejectionReason.REVIEW_COUNT_UNAVAILABLE,
            result.reasonCode()
        );
    }

    @Test
    void shouldFailAsUnavailableWhenReviewCountIsNegative() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithReviewCount(-1L),
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
            RejectionReason.REVIEW_COUNT_UNAVAILABLE,
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
                snapshotWithReviewCount(1500L),
                null
            )
        );
    }

    private OfferSnapshot snapshotWithReviewCount(
        Long reviewCount
    ) {
        return new OfferSnapshot(
            null,
            PRODUCT,
            OffsetDateTime.parse(
                "2026-09-20T14:25:00-03:00"
            ),
            Money.of("199.90"),
            null,
            null,
            null,
            4.7,
            reviewCount,
            "Amazon.com.br",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "amazon-deals",
            List.of()
        );
    }
}
