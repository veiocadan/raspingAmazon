package com.raspingamazon.domain.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreProfileTest {

    @Test
    void shouldCreateValidScoreProfile() {
        ScoreProfile profile = new ScoreProfile(
            "SCORE_V1",
            new BigDecimal("30"),
            new BigDecimal("25"),
            new BigDecimal("20"),
            new BigDecimal("15"),
            1000
        );

        assertEquals(
            "SCORE_V1",
            profile.version()
        );

        assertBigDecimalEquals(
            "30",
            profile.soldPercentageWeight()
        );

        assertBigDecimalEquals(
            "25",
            profile.cashDiscountWeight()
        );

        assertBigDecimalEquals(
            "20",
            profile.ratingWeight()
        );

        assertBigDecimalEquals(
            "15",
            profile.reviewCountWeight()
        );

        assertEquals(
            1000,
            profile.reviewCountFullScoreThreshold()
        );

        assertBigDecimalEquals(
            "90",
            profile.totalActiveWeight()
        );
    }

    @Test
    void shouldReturnWeightByFactorCode() {
        ScoreProfile profile = validProfile();

        assertBigDecimalEquals(
            "30",
            profile.weightFor(
                ScoreFactorCode.SOLD_PERCENTAGE
            )
        );

        assertBigDecimalEquals(
            "25",
            profile.weightFor(
                ScoreFactorCode.CASH_DISCOUNT
            )
        );

        assertBigDecimalEquals(
            "20",
            profile.weightFor(
                ScoreFactorCode.RATING
            )
        );

        assertBigDecimalEquals(
            "15",
            profile.weightFor(
                ScoreFactorCode.REVIEW_COUNT
            )
        );
    }

    @Test
    void shouldAllowTotalWeightBelowOneHundred() {
        ScoreProfile profile = new ScoreProfile(
            "SCORE_V1",
            new BigDecimal("30"),
            new BigDecimal("25"),
            new BigDecimal("20"),
            new BigDecimal("15"),
            1000
        );

        assertBigDecimalEquals(
            "90",
            profile.totalActiveWeight()
        );
    }

    @Test
    void shouldAllowTotalWeightEqualToOneHundred() {
        ScoreProfile profile = new ScoreProfile(
            "SCORE_FULL",
            new BigDecimal("30"),
            new BigDecimal("25"),
            new BigDecimal("20"),
            new BigDecimal("25"),
            1000
        );

        assertBigDecimalEquals(
            "100",
            profile.totalActiveWeight()
        );
    }

    @Test
    void shouldRejectTotalWeightAboveOneHundred() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreProfile(
                "SCORE_INVALID",
                new BigDecimal("30"),
                new BigDecimal("30"),
                new BigDecimal("30"),
                new BigDecimal("20"),
                1000
            )
        );
    }

    @Test
    void shouldRejectNegativeFactorWeight() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreProfile(
                "SCORE_INVALID",
                new BigDecimal("-1"),
                new BigDecimal("25"),
                new BigDecimal("20"),
                new BigDecimal("15"),
                1000
            )
        );
    }

    @Test
    void shouldRejectFactorWeightAboveOneHundred() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreProfile(
                "SCORE_INVALID",
                new BigDecimal("101"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1000
            )
        );
    }

    @Test
    void shouldRejectZeroReviewCountFullScoreThreshold() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreProfile(
                "SCORE_V1",
                new BigDecimal("30"),
                new BigDecimal("25"),
                new BigDecimal("20"),
                new BigDecimal("15"),
                0
            )
        );
    }

    @Test
    void shouldRejectNegativeReviewCountFullScoreThreshold() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreProfile(
                "SCORE_V1",
                new BigDecimal("30"),
                new BigDecimal("25"),
                new BigDecimal("20"),
                new BigDecimal("15"),
                -1
            )
        );
    }

    @Test
    void shouldRejectBlankVersion() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreProfile(
                " ",
                new BigDecimal("30"),
                new BigDecimal("25"),
                new BigDecimal("20"),
                new BigDecimal("15"),
                1000
            )
        );
    }

    @Test
    void shouldRejectNullWeight() {
        assertThrows(
            NullPointerException.class,
            () -> new ScoreProfile(
                "SCORE_V1",
                null,
                new BigDecimal("25"),
                new BigDecimal("20"),
                new BigDecimal("15"),
                1000
            )
        );
    }

    private static ScoreProfile validProfile() {
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
