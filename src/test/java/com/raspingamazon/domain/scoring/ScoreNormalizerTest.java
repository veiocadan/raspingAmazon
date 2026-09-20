package com.raspingamazon.domain.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreNormalizerTest {

    @Test
    void shouldNormalizePercentageWithoutChangingSemanticValue() {
        BigDecimal result = ScoreNormalizer.normalizePercentage(
            new BigDecimal("80")
        );

        assertEquals(
            new BigDecimal("80.0000"),
            result
        );
    }

    @Test
    void shouldNormalizeZeroPercentage() {
        BigDecimal result = ScoreNormalizer.normalizePercentage(
            BigDecimal.ZERO
        );

        assertEquals(
            new BigDecimal("0.0000"),
            result
        );
    }

    @Test
    void shouldNormalizeOneHundredPercentage() {
        BigDecimal result = ScoreNormalizer.normalizePercentage(
            new BigDecimal("100")
        );

        assertEquals(
            new BigDecimal("100.0000"),
            result
        );
    }

    @Test
    void shouldRejectPercentageBelowZero() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreNormalizer.normalizePercentage(
                new BigDecimal("-0.01")
            )
        );
    }

    @Test
    void shouldRejectPercentageAboveOneHundred() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreNormalizer.normalizePercentage(
                new BigDecimal("100.01")
            )
        );
    }

    @Test
    void shouldNormalizeRatingFromFivePointScale() {
        BigDecimal result = ScoreNormalizer.normalizeRating(
            new BigDecimal("4.5")
        );

        assertEquals(
            new BigDecimal("90.0000"),
            result
        );
    }

    @Test
    void shouldNormalizeMaximumRatingToOneHundred() {
        BigDecimal result = ScoreNormalizer.normalizeRating(
            new BigDecimal("5")
        );

        assertEquals(
            new BigDecimal("100.0000"),
            result
        );
    }

    @Test
    void shouldRejectRatingBelowZero() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreNormalizer.normalizeRating(
                new BigDecimal("-0.1")
            )
        );
    }

    @Test
    void shouldRejectRatingAboveFive() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreNormalizer.normalizeRating(
                new BigDecimal("5.1")
            )
        );
    }

    @Test
    void shouldNormalizeReviewCountProportionallyBelowThreshold() {
        BigDecimal result = ScoreNormalizer.normalizeReviewCount(
            500,
            1000
        );

        assertEquals(
            new BigDecimal("50.0000"),
            result
        );
    }

    @Test
    void shouldNormalizeReviewCountAtThresholdToOneHundred() {
        BigDecimal result = ScoreNormalizer.normalizeReviewCount(
            1000,
            1000
        );

        assertEquals(
            new BigDecimal("100.0000"),
            result
        );
    }

    @Test
    void shouldCapReviewCountAboveThresholdAtOneHundred() {
        BigDecimal result = ScoreNormalizer.normalizeReviewCount(
            5000,
            1000
        );

        assertEquals(
            new BigDecimal("100.0000"),
            result
        );
    }

    @Test
    void shouldRejectNegativeReviewCount() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreNormalizer.normalizeReviewCount(
                -1,
                1000
            )
        );
    }

    @Test
    void shouldRejectNonPositiveReviewCountThreshold() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreNormalizer.normalizeReviewCount(
                100,
                0
            )
        );
    }

    @Test
    void shouldCalculateWeightedContribution() {
        BigDecimal result = ScoreNormalizer.calculateContribution(
            new BigDecimal("90"),
            new BigDecimal("20")
        );

        assertEquals(
            new BigDecimal("18.0000"),
            result
        );
    }

    @Test
    void shouldRoundContributionToFourDecimalPlacesUsingHalfUp() {
        BigDecimal result = ScoreNormalizer.calculateContribution(
            new BigDecimal("33.3333"),
            new BigDecimal("15")
        );

        assertEquals(
            new BigDecimal("5.0000"),
            result
        );
    }

    @Test
    void shouldRejectNormalizedValueAboveOneHundredWhenCalculatingContribution() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreNormalizer.calculateContribution(
                new BigDecimal("100.01"),
                new BigDecimal("20")
            )
        );
    }

    @Test
    void shouldRejectWeightAboveOneHundredWhenCalculatingContribution() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreNormalizer.calculateContribution(
                new BigDecimal("90"),
                new BigDecimal("100.01")
            )
        );
    }
}
