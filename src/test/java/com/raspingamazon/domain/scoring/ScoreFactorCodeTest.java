package com.raspingamazon.domain.scoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreFactorCodeTest {

    @Test
    void shouldExposeStableScoreFactorCodes() {
        assertEquals(
            "SOLD_PERCENTAGE",
            ScoreFactorCode.SOLD_PERCENTAGE.name()
        );

        assertEquals(
            "CASH_DISCOUNT",
            ScoreFactorCode.CASH_DISCOUNT.name()
        );

        assertEquals(
            "RATING",
            ScoreFactorCode.RATING.name()
        );

        assertEquals(
            "REVIEW_COUNT",
            ScoreFactorCode.REVIEW_COUNT.name()
        );
    }
}
