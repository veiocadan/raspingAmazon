package com.raspingamazon.domain.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreInputTest {

    @Test
    void shouldAcceptValidInputWithSoldPercentage() {
        ScoreInput input = new ScoreInput(
            new BigDecimal("80"),
            new BigDecimal("20"),
            new BigDecimal("4.5"),
            500
        );

        assertTrue(input.hasSoldPercentage());
    }

    @Test
    void shouldAcceptMissingSoldPercentage() {
        ScoreInput input = new ScoreInput(
            null,
            new BigDecimal("20"),
            new BigDecimal("4.5"),
            500
        );

        assertFalse(input.hasSoldPercentage());
    }

    @Test
    void shouldRejectSoldPercentageAboveOneHundred() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreInput(
                new BigDecimal("100.01"),
                new BigDecimal("20"),
                new BigDecimal("4.5"),
                500
            )
        );
    }

    @Test
    void shouldRejectCashDiscountOutsidePercentageRange() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreInput(
                new BigDecimal("80"),
                new BigDecimal("-0.01"),
                new BigDecimal("4.5"),
                500
            )
        );
    }

    @Test
    void shouldRejectRatingAboveFive() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreInput(
                new BigDecimal("80"),
                new BigDecimal("20"),
                new BigDecimal("5.01"),
                500
            )
        );
    }

    @Test
    void shouldRejectNegativeReviewCount() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreInput(
                new BigDecimal("80"),
                new BigDecimal("20"),
                new BigDecimal("4.5"),
                -1
            )
        );
    }
}
