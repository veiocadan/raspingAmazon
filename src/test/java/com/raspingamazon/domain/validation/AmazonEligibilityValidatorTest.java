package com.raspingamazon.domain.validation;

import com.raspingamazon.domain.evaluation.RejectionReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmazonEligibilityValidatorTest {

    private final AmazonEligibilityValidator validator =
            new AmazonEligibilityValidator();

    @Test
    void shouldAcceptAmazonSellerAndAmazonDelivery() {
        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.AMAZON,
                        DeliveryType.AMAZON
                );

        assertTrue(result.eligible());
        assertEquals(null, result.rejectionReason());
    }

    @Test
    void shouldRejectThirdPartySeller() {
        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.THIRD_PARTY,
                        DeliveryType.AMAZON
                );

        assertFalse(result.eligible());
        assertEquals(
                RejectionReason.SELLER_THIRD_PARTY,
                result.rejectionReason()
        );
    }

    @Test
    void shouldRejectUnknownSeller() {
        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.UNKNOWN,
                        DeliveryType.AMAZON
                );

        assertFalse(result.eligible());
        assertEquals(
                RejectionReason.SELLER_UNKNOWN,
                result.rejectionReason()
        );
    }

    @Test
    void shouldRejectAmazonSellerWithThirdPartyDelivery() {
        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.AMAZON,
                        DeliveryType.THIRD_PARTY
                );

        assertFalse(result.eligible());
        assertEquals(
                RejectionReason.DELIVERY_THIRD_PARTY,
                result.rejectionReason()
        );
    }

    @Test
    void shouldRejectAmazonSellerWithUnknownDelivery() {
        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.AMAZON,
                        DeliveryType.UNKNOWN
                );

        assertFalse(result.eligible());
        assertEquals(
                RejectionReason.DELIVERY_UNKNOWN,
                result.rejectionReason()
        );
    }

    @Test
    void shouldRejectThirdPartySellerBeforeEvaluatingDelivery() {
        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.THIRD_PARTY,
                        DeliveryType.THIRD_PARTY
                );

        assertFalse(result.eligible());
        assertEquals(
                RejectionReason.SELLER_THIRD_PARTY,
                result.rejectionReason()
        );
    }

    @Test
    void shouldRejectUnknownSellerBeforeEvaluatingDelivery() {
        AmazonEligibilityResult result =
                validator.validate(
                        SellerType.UNKNOWN,
                        DeliveryType.UNKNOWN
                );

        assertFalse(result.eligible());
        assertEquals(
                RejectionReason.SELLER_UNKNOWN,
                result.rejectionReason()
        );
    }
}