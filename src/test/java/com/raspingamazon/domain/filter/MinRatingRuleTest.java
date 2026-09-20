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

class MinRatingRuleTest {

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

    private final MinRatingRule rule =
        new MinRatingRule();

    @Test
    void shouldPassWhenRatingIsAboveMinimum() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithRating(4.7),
                PROFILE
            );

        assertTrue(
            result.passed()
        );

        assertEquals(
            "MIN_RATING",
            result.ruleCode()
        );

        assertEquals(
            "4.7",
            result.observedValue()
        );

        assertEquals(
            "4.3",
            result.threshold()
        );

        assertNull(
            result.reasonCode()
        );
    }

    @Test
    void shouldPassWhenRatingEqualsMinimum() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithRating(4.3),
                PROFILE
            );

        assertTrue(
            result.passed()
        );

        assertEquals(
            "4.3",
            result.observedValue()
        );

        assertEquals(
            "4.3",
            result.threshold()
        );
    }

    @Test
    void shouldFailWhenRatingIsBelowMinimum() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithRating(4.2),
                PROFILE
            );

        assertFalse(
            result.passed()
        );

        assertEquals(
            "MIN_RATING",
            result.ruleCode()
        );

        assertEquals(
            "4.2",
            result.observedValue()
        );

        assertEquals(
            "4.3",
            result.threshold()
        );

        assertEquals(
            RejectionReason.RATING_BELOW_MINIMUM,
            result.reasonCode()
        );
    }

    @Test
    void shouldFailAsUnavailableWhenRatingIsNull() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithRating(null),
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
            "4.3",
            result.threshold()
        );

        assertEquals(
            RejectionReason.RATING_UNAVAILABLE,
            result.reasonCode()
        );
    }

    @Test
    void shouldFailAsUnavailableWhenRatingIsNotFinite() {

        EvaluationRuleResult result =
            rule.evaluate(
                snapshotWithRating(Double.NaN),
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
            RejectionReason.RATING_UNAVAILABLE,
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
                snapshotWithRating(4.7),
                null
            )
        );
    }

    private OfferSnapshot snapshotWithRating(
        Double rating
    ) {
        return new OfferSnapshot(
            null,
            PRODUCT,
            OffsetDateTime.parse(
                "2026-09-19T14:45:00-03:00"
            ),
            Money.of("199.90"),
            null,
            null,
            null,
            rating,
            1500L,
            "Amazon.com.br",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "amazon-deals",
            List.of()
        );
    }
}
