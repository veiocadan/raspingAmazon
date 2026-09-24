package com.raspingamazon.application.enrichment.contract;

import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductEnrichmentResultCustomerReviewTest {

    private static final OffsetDateTime ENRICHED_AT =
        OffsetDateTime.parse(
            "2026-09-24T18:00:05-03:00"
        );

    @Test
    void shouldTransportTypedRatingAndReviewCountEvidence() {

        ProductEnrichmentResult result =
            new ProductEnrichmentResult(
                "B0GVT7QXF7",
                sellerEvidence(),
                deliveryEvidence(),
                new RatingEvidence(
                    "4,8 de 5 estrelas",
                    4.8d,
                    "averageCustomerReviews_feature_div/acrPopover@title"
                ),
                new ReviewCountEvidence(
                    "618 Análises",
                    618L,
                    "averageCustomerReviews_feature_div/acrCustomerReviewText@aria-label"
                ),
                List.of(),
                "AMAZON_PRODUCT_PAGE",
                "https://www.amazon.com.br/dp/B0GVT7QXF7",
                ENRICHED_AT
            );

        assertEquals(
            4.8d,
            result.ratingEvidence().rating()
        );

        assertEquals(
            618L,
            result.reviewCountEvidence().reviewCount()
        );
    }

    @Test
    void compatibilityConstructorShouldKeepNewEvidenceUnavailable() {

        ProductEnrichmentResult result =
            new ProductEnrichmentResult(
                "B0GVT7QXF7",
                sellerEvidence(),
                deliveryEvidence(),
                "AMAZON_PRODUCT_PAGE",
                "https://www.amazon.com.br/dp/B0GVT7QXF7",
                ENRICHED_AT
            );

        assertFalse(
            result.ratingEvidence().available()
        );

        assertFalse(
            result.reviewCountEvidence().available()
        );
    }

    @Test
    void shouldRejectInvalidNormalizedEvidence() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new RatingEvidence(
                "6,0 de 5 estrelas",
                6.0d,
                "averageCustomerReviews_feature_div/acrPopover@title"
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ReviewCountEvidence(
                "-1 Análise",
                -1L,
                "averageCustomerReviews_feature_div/acrCustomerReviewText@aria-label"
            )
        );
    }

    private SellerEvidence sellerEvidence() {

        return new SellerEvidence(
            "Amazon.com.br",
            SellerType.AMAZON,
            "merchantInfoFeature"
        );
    }

    private DeliveryEvidence deliveryEvidence() {

        return new DeliveryEvidence(
            "Amazon",
            DeliveryType.AMAZON,
            "fulfillerInfoFeature"
        );
    }
}
