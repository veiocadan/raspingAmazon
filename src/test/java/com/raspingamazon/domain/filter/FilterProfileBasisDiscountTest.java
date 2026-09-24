package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.shared.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterProfileBasisDiscountTest {

    @Test
    void shouldCreateBasisDiscountProfile() {
        FilterProfile profile =
            FilterProfile.forBasisDiscount(
                "COMMERCIAL_FILTER_V2",
                Percentage.of("20"),
                new BigDecimal("4.30"),
                100
            );

        assertEquals(
            "COMMERCIAL_FILTER_V2",
            profile.version()
        );

        assertNull(
            profile.minCashDiscountPercentage()
        );

        assertEquals(
            Percentage.of("20"),
            profile.minBasisDiscountPercentage()
        );

        assertFalse(
            profile.usesCashDiscountRule()
        );

        assertTrue(
            profile.usesBasisDiscountRule()
        );
    }

    @Test
    void shouldPreserveLegacyCashConstructor() {
        FilterProfile profile =
            new FilterProfile(
                "COMMERCIAL_FILTER_V1",
                Percentage.of("20"),
                new BigDecimal("4.30"),
                100
            );

        assertTrue(
            profile.usesCashDiscountRule()
        );

        assertFalse(
            profile.usesBasisDiscountRule()
        );
    }

    @Test
    void shouldRejectProfileWithBothDiscountThresholds() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new FilterProfile(
                "INVALID",
                Percentage.of("20"),
                Percentage.of("20"),
                new BigDecimal("4.30"),
                100
            )
        );
    }

    @Test
    void shouldRejectProfileWithoutDiscountThreshold() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new FilterProfile(
                "INVALID",
                null,
                null,
                new BigDecimal("4.30"),
                100
            )
        );
    }
}
