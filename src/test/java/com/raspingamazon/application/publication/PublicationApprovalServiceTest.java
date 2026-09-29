package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.port.PublicationQueryPort;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationApprovalServiceTest {

    private static final long PUBLICATION_ID =
        100L;

    @Test
    void shouldApproveCreatedPublication() {

        Publication publication =
            createPublication(
                PUBLICATION_ID,
                PublicationStatus.CREATED
            );

        RecordingStatusRepository statusRepository =
            new RecordingStatusRepository();

        PublicationApprovalService service =
            new PublicationApprovalService(
                publicationId ->
                    Optional.of(
                        publication
                    ),
                statusRepository
            );

        Publication result =
            service.approve(
                PUBLICATION_ID
            );

        assertSame(
            publication,
            result
        );

        assertEquals(
            PublicationStatus.READY,
            result.status()
        );

        assertSame(
            publication,
            statusRepository.publication
        );

        assertEquals(
            PublicationStatus.CREATED,
            statusRepository.expectedStatus
        );

        assertEquals(
            1,
            statusRepository.callCount
        );
    }

    @Test
    void shouldRejectNonPositivePublicationIdBeforeQuery() {

        PublicationQueryPort queryPort =
            publicationId -> {
                throw new AssertionError(
                    "query port must not be called"
                );
            };

        PublicationStatusRepository statusRepository =
            (publication, expectedStatus) -> {
                throw new AssertionError(
                    "status repository must not be called"
                );
            };

        PublicationApprovalService service =
            new PublicationApprovalService(
                queryPort,
                statusRepository
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.approve(
                    0L
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.approve(
                    -1L
                )
        );
    }

    @Test
    void shouldRejectUnknownPublication() {

        PublicationStatusRepository statusRepository =
            (publication, expectedStatus) -> {
                throw new AssertionError(
                    "status repository must not be called"
                );
            };

        PublicationApprovalService service =
            new PublicationApprovalService(
                publicationId ->
                    Optional.empty(),
                statusRepository
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.approve(
                    PUBLICATION_ID
                )
        );
    }

    @Test
    void shouldRejectPublicationAlreadyReady() {

        assertInvalidDomainTransition(
            PublicationStatus.READY
        );
    }

    @Test
    void shouldRejectPublishedPublication() {

        assertInvalidDomainTransition(
            PublicationStatus.PUBLISHED
        );
    }

    @Test
    void shouldRejectFailedPublication() {

        assertInvalidDomainTransition(
            PublicationStatus.FAILED
        );
    }

    @Test
    void shouldPropagateConcurrentStatusConflict() {

        Publication publication =
            createPublication(
                PUBLICATION_ID,
                PublicationStatus.CREATED
            );

        PublicationStatusRepository statusRepository =
            (receivedPublication, expectedStatus) -> {

                assertSame(
                    publication,
                    receivedPublication
                );

                assertEquals(
                    PublicationStatus.CREATED,
                    expectedStatus
                );

                throw new IllegalStateException(
                    "simulated concurrent transition"
                );
            };

        PublicationApprovalService service =
            new PublicationApprovalService(
                publicationId ->
                    Optional.of(
                        publication
                    ),
                statusRepository
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.approve(
                    PUBLICATION_ID
                )
        );

        assertEquals(
            PublicationStatus.READY,
            publication.status()
        );
    }

    @Test
    void shouldRejectPublicationReturnedWithUnexpectedIdentity() {

        Publication publication =
            createPublication(
                999L,
                PublicationStatus.CREATED
            );

        PublicationStatusRepository statusRepository =
            (receivedPublication, expectedStatus) -> {
                throw new AssertionError(
                    "status repository must not be called"
                );
            };

        PublicationApprovalService service =
            new PublicationApprovalService(
                publicationId ->
                    Optional.of(
                        publication
                    ),
                statusRepository
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.approve(
                    PUBLICATION_ID
                )
        );

        assertEquals(
            PublicationStatus.CREATED,
            publication.status()
        );
    }

    private void assertInvalidDomainTransition(
        PublicationStatus initialStatus
    ) {

        Publication publication =
            createPublication(
                PUBLICATION_ID,
                initialStatus
            );

        RecordingStatusRepository statusRepository =
            new RecordingStatusRepository();

        PublicationApprovalService service =
            new PublicationApprovalService(
                publicationId ->
                    Optional.of(
                        publication
                    ),
                statusRepository
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.approve(
                    PUBLICATION_ID
                )
        );

        assertEquals(
            0,
            statusRepository.callCount
        );
    }

    private Publication createPublication(
        long publicationId,
        PublicationStatus status
    ) {

        Product product =
            new Product(
                1L,
                new Asin(
                    "B0APP18001"
                ),
                "Produto para aprovação",
                null,
                "https://www.amazon.com.br/dp/B0APP18001"
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                2L,
                product,
                OffsetDateTime.parse(
                    "2026-09-27T18:00:00-03:00"
                ),
                Money.of(
                    "99.90"
                ),
                null,
                null,
                null,
                4.8,
                1000L,
                "Amazon.com.br",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "publication-approval-test",
                List.of()
            );

        DealEvaluation evaluation =
            new DealEvaluation(
                3L,
                snapshot,
                true,
                null,
                "AMAZON_SELLER_DELIVERY_V1",
                null,
                List.of(
                    EvaluationRuleResult.passed(
                        "SELLER_IS_AMAZON",
                        "AMAZON",
                        "AMAZON"
                    )
                ),
                null,
                null,
                null,
                null,
                OffsetDateTime.parse(
                    "2026-09-27T18:05:00-03:00"
                )
            );

        return new Publication(
            publicationId,
            evaluation,
            "AMAZON_PUBLICATION_V1",
            "AMAZON_COMMERCIAL_PRESENTATION_V1",
            "AMAZON_AFFILIATE_LINK_V1",
            "Oferta pronta para revisão",
            "https://www.amazon.com.br/dp/B0APP18001?tag=test-20",
            status,
            OffsetDateTime.parse(
                "2026-09-27T18:10:00-03:00"
            )
        );
    }

    private static final class RecordingStatusRepository
        implements PublicationStatusRepository {

        private Publication publication;

        private PublicationStatus expectedStatus;

        private int callCount;

        @Override
        public void updateStatus(
            Publication publication,
            PublicationStatus expectedStatus
        ) {

            callCount++;

            this.publication =
                publication;

            this.expectedStatus =
                expectedStatus;
        }
    }
}
