package com.raspingamazon.domain.evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Testa o vocabulário controlado de motivos de rejeição.
 */
class RejectionReasonTest {

    @Test
    void shouldContainSellerUnknownReason() {

        assertNotNull(
            RejectionReason.SELLER_UNKNOWN
        );
    }

    @Test
    void shouldContainSellerThirdPartyReason() {

        assertNotNull(
            RejectionReason.SELLER_THIRD_PARTY
        );
    }

    @Test
    void shouldContainDeliveryUnknownReason() {

        assertNotNull(
            RejectionReason.DELIVERY_UNKNOWN
        );
    }

    @Test
    void shouldContainDeliveryThirdPartyReason() {

        assertNotNull(
            RejectionReason.DELIVERY_THIRD_PARTY
        );
    }

    @Test
    void shouldContainInsufficientDataReason() {

        assertNotNull(
            RejectionReason.INSUFFICIENT_DATA
        );
    }

    @Test
    void shouldPreserveHistoricalCashDiscountUnavailableReason() {

        assertNotNull(
            RejectionReason.CASH_DISCOUNT_UNAVAILABLE
        );
    }

    @Test
    void shouldPreserveHistoricalCashDiscountBelowMinimumReason() {

        assertNotNull(
            RejectionReason.CASH_DISCOUNT_BELOW_MINIMUM
        );
    }

    @Test
    void shouldContainBasisDiscountUnavailableReason() {

        assertNotNull(
            RejectionReason.BASIS_DISCOUNT_UNAVAILABLE
        );
    }

    @Test
    void shouldContainBasisDiscountBelowMinimumReason() {

        assertNotNull(
            RejectionReason.BASIS_DISCOUNT_BELOW_MINIMUM
        );
    }

    @Test
    void shouldContainRatingUnavailableReason() {

        assertNotNull(
            RejectionReason.RATING_UNAVAILABLE
        );
    }

    @Test
    void shouldContainRatingBelowMinimumReason() {

        assertNotNull(
            RejectionReason.RATING_BELOW_MINIMUM
        );
    }

    @Test
    void shouldContainReviewCountUnavailableReason() {

        assertNotNull(
            RejectionReason.REVIEW_COUNT_UNAVAILABLE
        );
    }

    @Test
    void shouldContainReviewCountBelowMinimumReason() {

        assertNotNull(
            RejectionReason.REVIEW_COUNT_BELOW_MINIMUM
        );
    }

    @Test
    void shouldContainExactlyThirteenRejectionReasons() {

        assertEquals(
            13,
            RejectionReason.values()
                .length
        );
    }
}
