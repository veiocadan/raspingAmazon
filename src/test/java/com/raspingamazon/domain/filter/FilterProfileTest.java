package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.shared.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterProfileTest {

    @Test
    void shouldCreateValidFilterProfile() {

        FilterProfile profile =
            new FilterProfile(
                "COMMERCIAL_FILTER_V1",
                Percentage.of("20"),
                new BigDecimal("4.3"),
                100L
            );

        assertEquals(
            "COMMERCIAL_FILTER_V1",
            profile.version()
        );

        assertEquals(
            Percentage.of("20"),
            profile.minCashDiscountPercentage()
        );

        assertEquals(
            new BigDecimal("4.3"),
            profile.minRating()
        );

        assertEquals(
            100L,
            profile.minReviewCount()
        );
    }

    @Test
    void shouldAcceptZeroThresholds() {

        FilterProfile profile =
            new FilterProfile(
                "COMMERCIAL_FILTER_ZERO_THRESHOLDS",
                Percentage.of("0"),
                BigDecimal.ZERO,
                0L
            );

        assertEquals(
            Percentage.of("0"),
            profile.minCashDiscountPercentage()
        );

        assertEquals(
            BigDecimal.ZERO,
            profile.minRating()
        );

        assertEquals(
            0L,
            profile.minReviewCount()
        );
    }

    @Test
    void shouldAcceptMaximumRating() {

        FilterProfile profile =
            new FilterProfile(
                "COMMERCIAL_FILTER_MAX_RATING",
                Percentage.of("20"),
                new BigDecimal("5"),
                100L
            );

        assertEquals(
            new BigDecimal("5"),
            profile.minRating()
        );
    }

    @Test
    void shouldRejectNullVersion() {

        assertThrows(
            NullPointerException.class,
            () -> new FilterProfile(
                null,
                Percentage.of("20"),
                new BigDecimal("4.3"),
                100L
            )
        );
    }

    @Test
    void shouldRejectBlankVersion() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new FilterProfile(
                "   ",
                Percentage.of("20"),
                new BigDecimal("4.3"),
                100L
            )
        );
    }

    @Test
    void shouldRejectNullCashDiscountThreshold() {

        assertThrows(
            NullPointerException.class,
            () -> new FilterProfile(
                "COMMERCIAL_FILTER_V1",
                null,
                new BigDecimal("4.3"),
                100L
            )
        );
    }

    @Test
    void shouldRejectNullRatingThreshold() {

        assertThrows(
            NullPointerException.class,
            () -> new FilterProfile(
                "COMMERCIAL_FILTER_V1",
                Percentage.of("20"),
                null,
                100L
            )
        );
    }

    @Test
    void shouldRejectRatingBelowZero() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new FilterProfile(
                "COMMERCIAL_FILTER_V1",
                Percentage.of("20"),
                new BigDecimal("-0.1"),
                100L
            )
        );
    }

    @Test
    void shouldRejectRatingAboveFive() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new FilterProfile(
                "COMMERCIAL_FILTER_V1",
                Percentage.of("20"),
                new BigDecimal("5.1"),
                100L
            )
        );
    }

    @Test
    void shouldRejectNegativeReviewCount() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new FilterProfile(
                "COMMERCIAL_FILTER_V1",
                Percentage.of("20"),
                new BigDecimal("4.3"),
                -1L
            )
        );
    }
}
