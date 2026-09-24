package com.raspingamazon.application.deal;

import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.enrichment.contract.RatingEvidence;
import com.raspingamazon.application.enrichment.contract.ReviewCountEvidence;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RatingReviewFallbackOfferSnapshotFactoryTest {

    private static final String ASIN =
        "B0GVT7QXF7";

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-24T18:00:00-03:00"
        );

    private static final OffsetDateTime ENRICHED_AT =
        OffsetDateTime.parse(
            "2026-09-24T18:00:05-03:00"
        );

    private final OfferSnapshotFactory factory =
        new OfferSnapshotFactory();

    @Test
    void shouldUseProductPageValuesOnlyWhenDealsValuesAreMissing() {

        OfferSnapshot snapshot =
            factory.create(
                product(),
                parsedDeal(
                    null,
                    null
                ),
                enrichment(
                    4.8d,
                    618L
                ),
                List.of()
            );

        assertEquals(
            4.8d,
            snapshot.rating()
        );

        assertEquals(
            618L,
            snapshot.reviewCount()
        );
    }

    @Test
    void shouldPreserveDealsValuesWhenBothSourcesAreAvailable() {

        OfferSnapshot snapshot =
            factory.create(
                product(),
                parsedDeal(
                    4.7d,
                    500L
                ),
                enrichment(
                    4.8d,
                    618L
                ),
                List.of()
            );

        assertEquals(
            4.7d,
            snapshot.rating()
        );

        assertEquals(
            500L,
            snapshot.reviewCount()
        );
    }

    @Test
    void shouldResolveRatingAndReviewCountIndependently() {

        OfferSnapshot snapshot =
            factory.create(
                product(),
                parsedDeal(
                    4.6d,
                    null
                ),
                enrichment(
                    4.8d,
                    618L
                ),
                List.of()
            );

        assertEquals(
            4.6d,
            snapshot.rating()
        );

        assertEquals(
            618L,
            snapshot.reviewCount()
        );
    }

    @Test
    void shouldKeepMissingValuesNullWhenNeitherSourceHasEvidence() {

        OfferSnapshot snapshot =
            factory.create(
                product(),
                parsedDeal(
                    null,
                    null
                ),
                new ProductEnrichmentResult(
                    ASIN,
                    sellerEvidence(),
                    deliveryEvidence(),
                    RatingEvidence.unavailable(),
                    ReviewCountEvidence.unavailable(),
                    List.of(),
                    "AMAZON_PRODUCT_PAGE",
                    "https://www.amazon.com.br/dp/" + ASIN,
                    ENRICHED_AT
                ),
                List.of()
            );

        assertNull(
            snapshot.rating()
        );

        assertNull(
            snapshot.reviewCount()
        );
    }

    private Product product() {

        return new Product(
            1L,
            new Asin(
                ASIN
            ),
            "Galaxy de teste",
            null,
            "https://www.amazon.com.br/dp/" + ASIN
        );
    }

    private ParsedDeal parsedDeal(
        Double rating,
        Long reviewCount
    ) {

        return new ParsedDeal(
            ASIN,
            "https://www.amazon.com.br/dp/" + ASIN,
            "Galaxy de teste",
            null,
            new BigDecimal(
                "1898.00"
            ),
            new BigDecimal(
                "3599.00"
            ),
            null,
            null,
            rating,
            reviewCount,
            COLLECTED_AT,
            "https://www.amazon.com.br/deals"
        );
    }

    private ProductEnrichmentResult enrichment(
        double rating,
        long reviewCount
    ) {

        return new ProductEnrichmentResult(
            ASIN,
            sellerEvidence(),
            deliveryEvidence(),
            new RatingEvidence(
                "4,8 de 5 estrelas",
                rating,
                "averageCustomerReviews_feature_div/acrPopover@title"
            ),
            new ReviewCountEvidence(
                "618 Análises",
                reviewCount,
                "averageCustomerReviews_feature_div/acrCustomerReviewText@aria-label"
            ),
            List.of(),
            "AMAZON_PRODUCT_PAGE",
            "https://www.amazon.com.br/dp/" + ASIN,
            ENRICHED_AT
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
