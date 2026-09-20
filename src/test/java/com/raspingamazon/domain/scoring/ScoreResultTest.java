package com.raspingamazon.domain.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreResultTest {

    @Test
    void shouldCreateScoreResultFromFactors() {
        ScoreResult result = ScoreResult.fromFactors(
            "SCORE_V1",
            List.of(
                available(
                    ScoreFactorCode.SOLD_PERCENTAGE,
                    "80",
                    "80",
                    "30",
                    "24"
                ),
                available(
                    ScoreFactorCode.CASH_DISCOUNT,
                    "20",
                    "20",
                    "25",
                    "5"
                ),
                available(
                    ScoreFactorCode.RATING,
                    "4.5",
                    "90",
                    "20",
                    "18"
                ),
                available(
                    ScoreFactorCode.REVIEW_COUNT,
                    "500",
                    "50",
                    "15",
                    "7.5"
                )
            )
        );

        assertEquals(
            "SCORE_V1",
            result.version()
        );

        assertBigDecimalEquals(
            "54.5000",
            result.score()
        );

        assertEquals(
            4,
            result.factors().size()
        );
    }

    @Test
    void shouldRoundFinalScoreToFourDecimalPlacesUsingHalfUp() {
        ScoreResult result = ScoreResult.fromFactors(
            "SCORE_V1",
            List.of(
                available(
                    ScoreFactorCode.RATING,
                    "4",
                    "80",
                    "20",
                    "12.34565"
                )
            )
        );

        assertEquals(
            new BigDecimal("12.3457"),
            result.score()
        );
    }

    @Test
    void shouldAcceptExplicitScoreWhenItMatchesContributions() {
        ScoreResult result = new ScoreResult(
            "SCORE_V1",
            new BigDecimal("18"),
            List.of(
                available(
                    ScoreFactorCode.RATING,
                    "4.5",
                    "90",
                    "20",
                    "18"
                )
            )
        );

        assertEquals(
            new BigDecimal("18.0000"),
            result.score()
        );
    }

    @Test
    void shouldRejectScoreThatDoesNotMatchContributions() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ScoreResult(
                "SCORE_V1",
                new BigDecimal("19"),
                List.of(
                    available(
                        ScoreFactorCode.RATING,
                        "4.5",
                        "90",
                        "20",
                        "18"
                    )
                )
            )
        );
    }

    @Test
    void shouldRejectDuplicateFactorCodes() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreResult.fromFactors(
                "SCORE_V1",
                List.of(
                    available(
                        ScoreFactorCode.RATING,
                        "4",
                        "80",
                        "20",
                        "16"
                    ),
                    available(
                        ScoreFactorCode.RATING,
                        "5",
                        "100",
                        "20",
                        "20"
                    )
                )
            )
        );
    }

    @Test
    void shouldRejectEmptyFactors() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreResult.fromFactors(
                "SCORE_V1",
                List.of()
            )
        );
    }

    @Test
    void shouldRejectBlankVersion() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ScoreResult.fromFactors(
                " ",
                List.of(
                    available(
                        ScoreFactorCode.RATING,
                        "5",
                        "100",
                        "20",
                        "20"
                    )
                )
            )
        );
    }

    @Test
    void shouldRejectNullFactor() {
        List<ScoreFactorResult> factors = new ArrayList<>();
        factors.add(null);

        assertThrows(
            NullPointerException.class,
            () -> ScoreResult.fromFactors(
                "SCORE_V1",
                factors
            )
        );
    }

    @Test
    void shouldDefensivelyCopyFactors() {
        List<ScoreFactorResult> original = new ArrayList<>();

        original.add(
            available(
                ScoreFactorCode.RATING,
                "4.5",
                "90",
                "20",
                "18"
            )
        );

        ScoreResult result = ScoreResult.fromFactors(
            "SCORE_V1",
            original
        );

        original.clear();

        assertEquals(
            1,
            result.factors().size()
        );

        assertNotSame(
            original,
            result.factors()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> result.factors().clear()
        );
    }

    @Test
    void shouldFindFactorByCodeAndReturnNullWhenAbsent() {
        ScoreResult result = ScoreResult.fromFactors(
            "SCORE_V1",
            List.of(
                available(
                    ScoreFactorCode.RATING,
                    "4.5",
                    "90",
                    "20",
                    "18"
                )
            )
        );

        ScoreFactorResult rating = result.factor(
            ScoreFactorCode.RATING
        );

        assertEquals(
            ScoreFactorCode.RATING,
            rating.code()
        );

        assertTrue(rating.isAvailable());

        assertNull(
            result.factor(
                ScoreFactorCode.SOLD_PERCENTAGE
            )
        );
    }

    private static ScoreFactorResult available(
        ScoreFactorCode code,
        String rawValue,
        String normalizedValue,
        String weight,
        String contribution
    ) {
        return ScoreFactorResult.available(
            code,
            new BigDecimal(rawValue),
            new BigDecimal(normalizedValue),
            new BigDecimal(weight),
            new BigDecimal(contribution)
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
