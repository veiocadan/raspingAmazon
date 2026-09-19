package com.raspingamazon.domain.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommercialFilterRuleCodeTest {

    @Test
    void shouldContainMinimumCashDiscountRule() {
        assertNotNull(
            CommercialFilterRuleCode.MIN_CASH_DISCOUNT
        );
    }

    @Test
    void shouldContainMinimumRatingRule() {
        assertNotNull(
            CommercialFilterRuleCode.MIN_RATING
        );
    }

    @Test
    void shouldContainMinimumReviewCountRule() {
        assertNotNull(
            CommercialFilterRuleCode.MIN_REVIEW_COUNT
        );
    }

    @Test
    void shouldContainExactlyThreeCommercialFilterRules() {
        assertEquals(
            3,
            CommercialFilterRuleCode.values().length
        );
    }
}
