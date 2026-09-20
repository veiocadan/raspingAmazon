package com.raspingamazon.domain.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreEngineTest {

    private final ScoreEngine engine = new ScoreEngine();

    @Test
    void shouldCalculateScoreUsingAllAvailableFactors() {
        ScoreResult result = engine.calculate(
            profile(),
            new ScoreInput(
                new BigDecimal("80"),
                new BigDecimal("20"),
                new BigDecimal("4.5"),
                500
            )
        );

        /*
         * soldPercentage:
         * 80 / 100 * 30 = 24
         *
         * cashDiscount:
         * 20 / 100 * 25 = 5
         *
         * rating:
         * 4.5 / 5 * 100 = 90
         * 90 / 100 * 20 = 18
         *
         * reviewCount:
         * 500 / 1000 * 100 = 50
         * 50 / 100 * 15 = 7.5
         *
         * total = 54.5
         */
        assertBigDecimalEquals(
            "54.5000",
            result.score()
        );

        assertEquals(
            "SCORE_V1",
            result.version()
        );

        assertEquals(
            4,
            result.factors().size()
        );
    }

    @Test
    void shouldMarkMissingSoldPercentageAsUnavailable() {
        ScoreResult result = engine.calculate(
            profile(),
            new ScoreInput(
                null,
                new BigDecimal("20"),
                new BigDecimal("4.5"),
                500
            )
        );

        ScoreFactorResult factor = result.factor(
            ScoreFactorCode.SOLD_PERCENTAGE
        );

        assertFalse(factor.isAvailable());

        assertEquals(
            ScoreFactorStatus.UNAVAILABLE,
            factor.status()
        );

        assertBigDecimalEquals(
            "0",
            factor.contribution()
        );
    }

    @Test
    void shouldNotTreatMissingSoldPercentageAsObservedZero() {
        ScoreResult unavailableResult = engine.calculate(
            profile(),
            new ScoreInput(
                null,
                new BigDecimal("20"),
                new BigDecimal("4.5"),
                500
            )
        );

        ScoreResult observedZeroResult = engine.calculate(
            profile(),
            new ScoreInput(
                BigDecimal.ZERO,
                new BigDecimal("20"),
                new BigDecimal("4.5"),
                500
            )
        );

        ScoreFactorResult unavailable =
            unavailableResult.factor(
                ScoreFactorCode.SOLD_PERCENTAGE
            );

        ScoreFactorResult observedZero =
            observedZeroResult.factor(
                ScoreFactorCode.SOLD_PERCENTAGE
            );

        assertEquals(
            ScoreFactorStatus.UNAVAILABLE,
            unavailable.status()
        );

        assertEquals(
            ScoreFactorStatus.AVAILABLE,
            observedZero.status()
        );

        assertTrue(observedZero.isAvailable());

        assertBigDecimalEquals(
            "0",
            unavailable.contribution()
        );

        assertBigDecimalEquals(
            "0",
            observedZero.contribution()
        );
    }

    @Test
    void shouldNormalizeRatingBeforeCalculatingContribution() {
        ScoreResult result = engine.calculate(
            profile(),
            new ScoreInput(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("4.5"),
                0
            )
        );

        ScoreFactorResult rating = result.factor(
            ScoreFactorCode.RATING
        );

        assertBigDecimalEquals(
            "4.5",
            rating.rawValue()
        );

        assertBigDecimalEquals(
            "90.0000",
            rating.normalizedValue()
        );

        assertBigDecimalEquals(
            "20",
            rating.weight()
        );

        assertBigDecimalEquals(
            "18.0000",
            rating.contribution()
        );
    }

    @Test
    void shouldCapReviewCountAtConfiguredThreshold() {
        ScoreResult result = engine.calculate(
            profile(),
            new ScoreInput(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                5000
            )
        );

        ScoreFactorResult reviews = result.factor(
            ScoreFactorCode.REVIEW_COUNT
        );

        assertBigDecimalEquals(
            "5000",
            reviews.rawValue()
        );

        assertBigDecimalEquals(
            "100.0000",
            reviews.normalizedValue()
        );

        assertBigDecimalEquals(
            "15.0000",
            reviews.contribution()
        );
    }

    @Test
    void shouldUseWeightsFromProfileInsteadOfHardcodedWeights() {
        ScoreProfile customProfile = new ScoreProfile(
            "CUSTOM_SCORE",
            new BigDecimal("10"),
            new BigDecimal("10"),
            new BigDecimal("10"),
            new BigDecimal("10"),
            100
        );

        ScoreResult result = engine.calculate(
            customProfile,
            new ScoreInput(
                new BigDecimal("100"),
                new BigDecimal("100"),
                new BigDecimal("5"),
                100
            )
        );

        assertEquals(
            "CUSTOM_SCORE",
            result.version()
        );

        assertBigDecimalEquals(
            "40.0000",
            result.score()
        );
    }

    @Test
    void shouldUseReviewCountThresholdFromProfile() {
        ScoreProfile customProfile = new ScoreProfile(
            "CUSTOM_SCORE",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("20"),
            200
        );

        ScoreResult result = engine.calculate(
            customProfile,
            new ScoreInput(
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                100
            )
        );

        ScoreFactorResult reviews = result.factor(
            ScoreFactorCode.REVIEW_COUNT
        );

        assertBigDecimalEquals(
            "50.0000",
            reviews.normalizedValue()
        );

        assertBigDecimalEquals(
            "10.0000",
            reviews.contribution()
        );
    }

    @Test
    void shouldProduceZeroScoreWhenAllAvailableValuesAreZeroAndSoldPercentageIsUnavailable() {
        ScoreResult result = engine.calculate(
            profile(),
            new ScoreInput(
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0
            )
        );

        assertBigDecimalEquals(
            "0.0000",
            result.score()
        );
    }

    @Test
    void shouldRejectNullProfile() {
        assertThrows(
            NullPointerException.class,
            () -> engine.calculate(
                null,
                new ScoreInput(
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0
                )
            )
        );
    }

    @Test
    void shouldRejectNullInput() {
        assertThrows(
            NullPointerException.class,
            () -> engine.calculate(
                profile(),
                null
            )
        );
    }

    private static ScoreProfile profile() {
        /*
         * 1000 é apenas um valor de fixture para testar a matemática.
         * Ainda não representa a configuração operacional oficial.
         */
        return new ScoreProfile(
            "SCORE_V1",
            new BigDecimal("30"),
            new BigDecimal("25"),
            new BigDecimal("20"),
            new BigDecimal("15"),
            1000
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
