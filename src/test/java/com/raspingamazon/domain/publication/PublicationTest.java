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
    void shouldCreatePublicationWithCreatedStatus() {
        assertEquals(
                PublicationStatus.CREATED,
                createPublication(
                        PublicationStatus.CREATED
                ).status()
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

    private Publication createPublication(
            PublicationStatus status
    ) {

        Product product =
                new Product(
                        1L,
                        new Asin("B000000001"),
                        "Produto de teste",
                        "https://example.com/image.jpg",
                        "https://example.com/product"
                );

        OfferSnapshot snapshot =
                new OfferSnapshot(
                        1L,
                        product,
                        OffsetDateTime.now(),
                        Money.of("100.00"),
                        Money.of("120.00"),
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

        DealEvaluation evaluation =
                new DealEvaluation(
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
                        OffsetDateTime.now()
                );

        return new Publication(
                1L,
                evaluation,
                "template-v1",
                "Oferta de teste",
                "https://example.com/affiliate",
                status,
                OffsetDateTime.now()
        );
    }
}