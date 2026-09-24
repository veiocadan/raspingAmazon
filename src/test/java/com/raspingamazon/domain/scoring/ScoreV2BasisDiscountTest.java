package com.raspingamazon.domain.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreV2BasisDiscountTest {

    private final ScoreEngine engine =
        new ScoreEngine();

    @Test
    void shouldCalculateScoreV2UsingBasisDiscountFactor() {
        ScoreProfile profile =
            ScoreProfile.forBasisDiscount(
                "SCORE_V2",
                new BigDecimal("30"),
                new BigDecimal("25"),
                new BigDecimal("20"),
                new BigDecimal("15"),
                1000
            );

        ScoreInput input =
            ScoreInput.forBasisDiscount(
                new BigDecimal("80"),
                new BigDecimal("20"),
                new BigDecimal("4.5"),
                500
            );

        ScoreResult result =
            engine.calculate(
                profile,
                input
            );

        assertEquals(
            "SCORE_V2",
            result.version()
        );

        assertEquals(
            4,
            result.factors().size()
        );

        assertTrue(
            result.factor(
                ScoreFactorCode.BASIS_DISCOUNT
            ).isAvailable()
        );

        assertBigDecimalEquals(
            "20",
            result.factor(
                ScoreFactorCode.BASIS_DISCOUNT
            ).rawValue()
        );

        assertBigDecimalEquals(
            "5.0000",
            result.factor(
                ScoreFactorCode.BASIS_DISCOUNT
            ).contribution()
        );

        assertBigDecimalEquals(
            "54.5000",
            result.score()
        );
    }

    @Test
    void shouldPreserveCashDiscountFactorForScoreV1() {
        ScoreProfile profile =
            new ScoreProfile(
                "SCORE_V1",
                new BigDecimal("30"),
                new BigDecimal("25"),
                new BigDecimal("20"),
                new BigDecimal("15"),
                1000
            );

        ScoreInput input =
            new ScoreInput(
                new BigDecimal("80"),
                new BigDecimal("20"),
                new BigDecimal("4.5"),
                500
            );

        ScoreResult result =
            engine.calculate(
                profile,
                input
            );

        assertTrue(
            result.factor(
                ScoreFactorCode.CASH_DISCOUNT
            ).isAvailable()
        );

        assertBigDecimalEquals(
            "54.5000",
            result.score()
        );
    }

    @Test
    void shouldRejectMismatchedProfileAndInputSemantics() {
        ScoreProfile profile =
            ScoreProfile.forBasisDiscount(
                "SCORE_V2",
                new BigDecimal("30"),
                new BigDecimal("25"),
                new BigDecimal("20"),
                new BigDecimal("15"),
                1000
            );

        ScoreInput cashInput =
            new ScoreInput(
                new BigDecimal("80"),
                new BigDecimal("20"),
                new BigDecimal("4.5"),
                500
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> engine.calculate(
                profile,
                cashInput
            )
        );
    }

    private static void assertBigDecimalEquals(
        String expected,
        BigDecimal actual
    ) {
        assertEquals(
            0,
            new BigDecimal(expected)
                .compareTo(actual)
        );
    }
}
