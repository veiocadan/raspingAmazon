package com.raspingamazon.domain.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreFactorResultTest {

    @Test
    void shouldCreateAvailableFactor() {
        ScoreFactorResult result = ScoreFactorResult.available(
            ScoreFactorCode.RATING,
            new BigDecimal("4.5"),
            new BigDecimal("90"),
            new BigDecimal("20"),
            new BigDecimal("18")
        );

        assertEquals(
            ScoreFactorCode.RATING,
            result.code()
        );

        assertEquals(
            ScoreFactorStatus.AVAILABLE,
            result.status()
        );

        assertBigDecimalEquals(
            "4.5",
            result.rawValue()
        );

        assertBigDecimalEquals(
            "90",
            result.normalizedValue()
        );

        assertBigDecimalEquals(
            "20",
            result.weight()
        );

        assertBigDecimalEquals(
            "18",
            result.contribution()
        );

        assertTrue(result.isAvailable());
    }

    @Test
    void shouldCreateUnavailableFactorWithZeroContribution() {
        ScoreFactorResult result = ScoreFactorResult.unavailable(
            ScoreFactorCode.SOLD_PERCENTAGE,
            new BigDecimal("30")
        );

        assertEquals(
            ScoreFactorCode.SOLD_PERCENTAGE,
            result.code()
        );

        assertEquals(
            ScoreFactorStatus.UNAVAILABLE,
            result.status()
        );

        assertNull(result.rawValue());
        assertNull(result.normalizedValue());

        assertBigDecimalEquals(
            "30",
            result.weight()
        );

        assertBigDecimalEquals(
            "0",
            result.contribution()
        );

        assertFalse(result.isAvailable());
    }

    @Test
    void shouldRejectAvailableFactorWithoutRawValue() {
        assertThrows(
            NullPointerException.class,
            () -> new ScoreFactorResult(
                ScoreFactorCode.RATING,
                ScoreFactorStatus.AVAILABLE,
                null,
                new BigDecimal("90"),
                new BigDecimal("20"),
                new BigDecimal("18")
            )
        );
    }

    @Test
    void shouldRejectAvailableFactorWithoutNormalizedValue() {
        assertThrows(
            NullPointerException.class,
            () -> new ScoreFactorResult(
                ScoreFactorCode.RATING,
                ScoreFactorStatus.AVAILABLE,
                new BigDecimal("4.5"),
                null,
                new BigDecimal("20"),
                new BigDecimal("18")
            )
        );
    }

    @Test
    void shouldRejectNormalizedValueAboveOneHundred() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreFactorResult.available(
                ScoreFactorCode.RATING,
                new BigDecimal("5"),
                new BigDecimal("100.01"),
                new BigDecimal("20"),
                new BigDecimal("20")
            )
        );
    }

    @Test
    void shouldRejectNormalizedValueBelowZero() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreFactorResult.available(
                ScoreFactorCode.RATING,
                new BigDecimal("1"),
                new BigDecimal("-1"),
                new BigDecimal("20"),
                BigDecimal.ZERO
            )
        );
    }

    @Test
    void shouldRejectWeightAboveOneHundred() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreFactorResult.available(
                ScoreFactorCode.RATING,
                new BigDecimal("5"),
                new BigDecimal("100"),
                new BigDecimal("100.01"),
                new BigDecimal("20")
            )
        );
    }

    @Test
    void shouldRejectNegativeWeight() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreFactorResult.available(
                ScoreFactorCode.RATING,
                new BigDecimal("5"),
                new BigDecimal("100"),
                new BigDecimal("-1"),
                BigDecimal.ZERO
            )
        );
    }

    @Test
    void shouldRejectContributionGreaterThanWeight() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreFactorResult.available(
                ScoreFactorCode.RATING,
                new BigDecimal("5"),
                new BigDecimal("100"),
                new BigDecimal("20"),
                new BigDecimal("20.01")
            )
        );
    }

    @Test
    void shouldRejectNegativeContribution() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreFactorResult.available(
                ScoreFactorCode.RATING,
                new BigDecimal("5"),
                new BigDecimal("100"),
                new BigDecimal("20"),
                new BigDecimal("-0.01")
            )
        );
    }

    @Test
    void shouldRejectUnavailableFactorWithRawValue() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreFactorResult(
                ScoreFactorCode.SOLD_PERCENTAGE,
                ScoreFactorStatus.UNAVAILABLE,
                BigDecimal.ZERO,
                null,
                new BigDecimal("30"),
                BigDecimal.ZERO
            )
        );
    }

    @Test
    void shouldRejectUnavailableFactorWithNormalizedValue() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreFactorResult(
                ScoreFactorCode.SOLD_PERCENTAGE,
                ScoreFactorStatus.UNAVAILABLE,
                null,
                BigDecimal.ZERO,
                new BigDecimal("30"),
                BigDecimal.ZERO
            )
        );
    }

    @Test
    void shouldRejectUnavailableFactorWithNonZeroContribution() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreFactorResult(
                ScoreFactorCode.SOLD_PERCENTAGE,
                ScoreFactorStatus.UNAVAILABLE,
                null,
                null,
                new BigDecimal("30"),
                new BigDecimal("1")
            )
        );
    }

    @Test
    void shouldRejectNegativeRawValue() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreFactorResult.available(
                ScoreFactorCode.REVIEW_COUNT,
                new BigDecimal("-1"),
                BigDecimal.ZERO,
                new BigDecimal("15"),
                BigDecimal.ZERO
            )
        );
    }

    private static void assertBigDecimalEquals(
        String expected,
        BigDecimal actual
    ) {
        assertEquals(
            0,
            new BigDecimal(expected).compareTo(actual)
        );
    }
}
