package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.validation.AmazonEligibilityResult;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class SellerDeliveryUnknownSemanticsTest {

    @Test
    void missingSellerAndDeliveryShouldRemainAuditableUnknownEvidence() {

        String html =
            """
            <html>
                <body>
                    <span id="productTitle">
                        Existing product without current main offer
                    </span>
                </body>
            </html>
            """;

        AmazonProductPageDocumentValidator documentValidator =
            new AmazonProductPageDocumentValidator();

        assertDoesNotThrow(
            () -> documentValidator.validate(
                html
            )
        );

        AmazonProductPageParser.ParsedProductOffer parsedOffer =
            new AmazonProductPageParser()
                .parse(
                    html
                );

        assertEquals(
            SellerType.UNKNOWN,
            parsedOffer.sellerEvidence()
                .sellerType()
        );

        assertNull(
            parsedOffer.sellerEvidence()
                .rawValue()
        );

        assertNull(
            parsedOffer.sellerEvidence()
                .source()
        );

        assertEquals(
            DeliveryType.UNKNOWN,
            parsedOffer.deliveryEvidence()
                .deliveryType()
        );

        assertNull(
            parsedOffer.deliveryEvidence()
                .rawValue()
        );

        assertNull(
            parsedOffer.deliveryEvidence()
                .source()
        );

        AmazonEligibilityResult eligibility =
            new AmazonEligibilityValidator()
                .validate(
                    parsedOffer.sellerEvidence()
                        .sellerType(),
                    parsedOffer.deliveryEvidence()
                        .deliveryType()
                );

        assertFalse(
            eligibility.eligible()
        );

        assertEquals(
            RejectionReason.SELLER_UNKNOWN,
            eligibility.rejectionReason()
        );

        assertEquals(
            RejectionReason.SELLER_UNKNOWN,
            eligibility.ruleResults()
                .get(0)
                .reasonCode()
        );

        assertEquals(
            RejectionReason.DELIVERY_UNKNOWN,
            eligibility.ruleResults()
                .get(1)
                .reasonCode()
        );
    }
}
