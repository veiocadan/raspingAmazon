package com.raspingamazon.domain.publication;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationTest {

    @Test
    void shouldCreatePublicationWithGenerationVersions() {

        Publication publication =
            createPublication(
                PublicationStatus.CREATED
            );

        assertEquals(
            PublicationStatus.CREATED,
            publication.status()
        );

        assertEquals(
            "AMAZON_PUBLICATION_V1",
            publication.templateVersion()
        );

        assertEquals(
            "AMAZON_COMMERCIAL_PRESENTATION_V1",
            publication.commercialPresentationVersion()
        );

        assertEquals(
            "AMAZON_AFFILIATE_LINK_V1",
            publication.affiliateLinkVersion()
        );

        assertEquals(
            "https://www.amazon.com.br/dp/B000000001?tag=test-20",
            publication.affiliateUrl()
        );
    }

    @Test
    void shouldMoveFromCreatedToReady() {

        Publication publication =
            createPublication(
                PublicationStatus.CREATED
            );

        publication.markReady();

        assertEquals(
            PublicationStatus.READY,
            publication.status()
        );
    }

    @Test
    void shouldMoveFromReadyToPublished() {

        Publication publication =
            createPublication(
                PublicationStatus.READY
            );

        publication.markPublished();

        assertEquals(
            PublicationStatus.PUBLISHED,
            publication.status()
        );
    }

    @Test
    void shouldMoveFromReadyToFailed() {

        Publication publication =
            createPublication(
                PublicationStatus.READY
            );

        publication.markFailed();

        assertEquals(
            PublicationStatus.FAILED,
            publication.status()
        );
    }

    @Test
    void shouldRetryFailedPublication() {

        Publication publication =
            createPublication(
                PublicationStatus.FAILED
            );

        publication.retry();

        assertEquals(
            PublicationStatus.READY,
            publication.status()
        );
    }

    @Test
    void shouldRejectReadyTransitionFromPublished() {

        Publication publication =
            createPublication(
                PublicationStatus.PUBLISHED
            );

        assertThrows(
            IllegalStateException.class,
            publication::markReady
        );
    }

    @Test
    void shouldRejectPublishingAlreadyPublishedPublication() {

        Publication publication =
            createPublication(
                PublicationStatus.PUBLISHED
            );

        assertThrows(
            IllegalStateException.class,
            publication::markPublished
        );
    }

    @Test
    void shouldRejectPublishingFailedPublicationDirectly() {

        Publication publication =
            createPublication(
                PublicationStatus.FAILED
            );

        assertThrows(
            IllegalStateException.class,
            publication::markPublished
        );
    }

    @Test
    void shouldRejectPublishingCreatedPublicationDirectly() {

        Publication publication =
            createPublication(
                PublicationStatus.CREATED
            );

        assertThrows(
            IllegalStateException.class,
            publication::markPublished
        );
    }

    @Test
    void shouldRejectFailingCreatedPublicationDirectly() {

        Publication publication =
            createPublication(
                PublicationStatus.CREATED
            );

        assertThrows(
            IllegalStateException.class,
            publication::markFailed
        );
    }

    @Test
    void shouldRejectRetryFromReady() {

        Publication publication =
            createPublication(
                PublicationStatus.READY
            );

        assertThrows(
            IllegalStateException.class,
            publication::retry
        );
    }

    @Test
    void shouldRejectRetryFromCreated() {

        Publication publication =
            createPublication(
                PublicationStatus.CREATED
            );

        assertThrows(
            IllegalStateException.class,
            publication::retry
        );
    }

    @Test
    void shouldRejectRetryFromPublished() {

        Publication publication =
            createPublication(
                PublicationStatus.PUBLISHED
            );

        assertThrows(
            IllegalStateException.class,
            publication::retry
        );
    }

    @Test
    void shouldRejectBlankGenerationVersions() {

        DealEvaluation evaluation =
            createEvaluation();

        OffsetDateTime createdAt =
            OffsetDateTime.parse(
                "2026-09-23T19:20:00-03:00"
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new Publication(
                null,
                evaluation,
                " ",
                "AMAZON_COMMERCIAL_PRESENTATION_V1",
                "AMAZON_AFFILIATE_LINK_V1",
                "Oferta",
                "https://www.amazon.com.br/dp/B000000001?tag=test-20",
                PublicationStatus.CREATED,
                createdAt
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new Publication(
                null,
                evaluation,
                "AMAZON_PUBLICATION_V1",
                " ",
                "AMAZON_AFFILIATE_LINK_V1",
                "Oferta",
                "https://www.amazon.com.br/dp/B000000001?tag=test-20",
                PublicationStatus.CREATED,
                createdAt
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new Publication(
                null,
                evaluation,
                "AMAZON_PUBLICATION_V1",
                "AMAZON_COMMERCIAL_PRESENTATION_V1",
                " ",
                "Oferta",
                "https://www.amazon.com.br/dp/B000000001?tag=test-20",
                PublicationStatus.CREATED,
                createdAt
            )
        );
    }

    @Test
    void shouldRejectBlankAffiliateUrl() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new Publication(
                null,
                createEvaluation(),
                "AMAZON_PUBLICATION_V1",
                "AMAZON_COMMERCIAL_PRESENTATION_V1",
                "AMAZON_AFFILIATE_LINK_V1",
                "Oferta",
                " ",
                PublicationStatus.CREATED,
                OffsetDateTime.parse(
                    "2026-09-23T19:20:00-03:00"
                )
            )
        );
    }

    private Publication createPublication(
        PublicationStatus status
    ) {

        return new Publication(
            1L,
            createEvaluation(),
            "AMAZON_PUBLICATION_V1",
            "AMAZON_COMMERCIAL_PRESENTATION_V1",
            "AMAZON_AFFILIATE_LINK_V1",
            "Oferta de teste",
            "https://www.amazon.com.br/dp/B000000001?tag=test-20",
            status,
            OffsetDateTime.parse(
                "2026-09-23T19:20:00-03:00"
            )
        );
    }

    private DealEvaluation createEvaluation() {

        Product product =
            new Product(
                1L,
                new Asin(
                    "B000000001"
                ),
                "Produto de teste",
                "https://example.com/image.jpg",
                "https://www.amazon.com.br/dp/B000000001"
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                1L,
                product,
                OffsetDateTime.parse(
                    "2026-09-23T19:00:00-03:00"
                ),
                Money.of(
                    "100.00"
                ),
                Money.of(
                    "120.00"
                ),
                null,
                null,
                4.5,
                100L,
                "Amazon.com.br",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "TEST",
                List.of()
            );

        List<EvaluationRuleResult> rules =
            List.of(
                EvaluationRuleResult.passed(
                    "SELLER_IS_AMAZON",
                    "AMAZON",
                    "AMAZON"
                ),
                EvaluationRuleResult.passed(
                    "DELIVERY_IS_AMAZON",
                    "AMAZON",
                    "AMAZON"
                )
            );

        return new DealEvaluation(
            1L,
            snapshot,
            true,
            null,
            "AMAZON_SELLER_DELIVERY_V1",
            null,
            rules,
            null,
            null,
            null,
            null,
            OffsetDateTime.parse(
                "2026-09-23T19:05:00-03:00"
            )
        );
    }
}
