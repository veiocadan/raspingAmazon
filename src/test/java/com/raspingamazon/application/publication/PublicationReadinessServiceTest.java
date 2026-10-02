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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationReadinessServiceTest {

    private static final long PUBLICATION_ID =
        100L;

    @Test
    void shouldAutomaticallyReleaseCreatedPublication() {

        Publication publication =
            createPublication(
                PUBLICATION_ID,
                PublicationStatus.CREATED
            );

        RecordingStatusRepository statusRepository =
            new RecordingStatusRepository();

        PublicationReadinessService service =
            new PublicationReadinessService(
                publicationId ->
                    Optional.of(
                        publication
                    ),
                statusRepository
            );

        Publication result =
            service.ensureReady(
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
    void shouldBeIdempotentWhenPublicationIsAlreadyReady() {

        Publication publication =
            createPublication(
                PUBLICATION_ID,
                PublicationStatus.READY
            );

        RecordingStatusRepository statusRepository =
            new RecordingStatusRepository();

        PublicationReadinessService service =
            new PublicationReadinessService(
                publicationId ->
                    Optional.of(
                        publication
                    ),
                statusRepository
            );

        Publication result =
            service.ensureReady(
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

        assertEquals(
            0,
            statusRepository.callCount
        );
    }

    @Test
    void shouldRecoverWhenConcurrentExecutionAlreadyMadePublicationReady() {

        Publication initiallyLoaded =
            createPublication(
                PUBLICATION_ID,
                PublicationStatus.CREATED
            );

        Publication concurrentlyReloaded =
            createPublication(
                PUBLICATION_ID,
                PublicationStatus.READY
            );

        AtomicInteger queryCalls =
            new AtomicInteger();

        PublicationQueryPort queryPort =
            publicationId -> {

                int call =
                    queryCalls.incrementAndGet();

                if (call == 1) {

                    return Optional.of(
                        initiallyLoaded
                    );
                }

                return Optional.of(
                    concurrentlyReloaded
                );
            };

        PublicationStatusRepository statusRepository =
            (publication, expectedStatus) -> {

                throw new IllegalStateException(
                    "simulated concurrent transition"
                );
            };

        PublicationReadinessService service =
            new PublicationReadinessService(
                queryPort,
                statusRepository
            );

        Publication result =
            service.ensureReady(
                PUBLICATION_ID
            );

        assertSame(
            concurrentlyReloaded,
            result
        );

        assertEquals(
            PublicationStatus.READY,
            result.status()
        );

        assertEquals(
            2,
            queryCalls.get()
        );
    }

    @Test
    void shouldPreserveConcurrentFailureWhenReloadedStateIsNotReady() {

        Publication initiallyLoaded =
            createPublication(
                PUBLICATION_ID,
                PublicationStatus.CREATED
            );

        Publication concurrentlyReloaded =
            createPublication(
                PUBLICATION_ID,
                PublicationStatus.FAILED
            );

        AtomicInteger queryCalls =
            new AtomicInteger();

        PublicationQueryPort queryPort =
            publicationId -> {

                if (queryCalls.incrementAndGet()
                    == 1) {

                    return Optional.of(
                        initiallyLoaded
                    );
                }

                return Optional.of(
                    concurrentlyReloaded
                );
            };

        IllegalStateException expected =
            new IllegalStateException(
                "simulated concurrent transition"
            );

        PublicationStatusRepository statusRepository =
            (publication, expectedStatus) -> {
                throw expected;
            };

        PublicationReadinessService service =
            new PublicationReadinessService(
                queryPort,
                statusRepository
            );

        IllegalStateException actual =
            assertThrows(
                IllegalStateException.class,
                () ->
                    service.ensureReady(
                        PUBLICATION_ID
                    )
            );

        assertSame(
            expected,
            actual
        );
    }

    @Test
    void shouldRejectPublishedPublication() {

        assertUnsupportedState(
            PublicationStatus.PUBLISHED
        );
    }

    @Test
    void shouldRejectFailedPublication() {

        assertUnsupportedState(
            PublicationStatus.FAILED
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

        PublicationReadinessService service =
            new PublicationReadinessService(
                queryPort,
                statusRepository
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.ensureReady(
                    0L
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.ensureReady(
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

        PublicationReadinessService service =
            new PublicationReadinessService(
                publicationId ->
                    Optional.empty(),
                statusRepository
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.ensureReady(
                    PUBLICATION_ID
                )
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

        PublicationReadinessService service =
            new PublicationReadinessService(
                publicationId ->
                    Optional.of(
                        publication
                    ),
                statusRepository
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.ensureReady(
                    PUBLICATION_ID
                )
        );

        assertEquals(
            PublicationStatus.CREATED,
            publication.status()
        );
    }

    private void assertUnsupportedState(
        PublicationStatus status
    ) {

        Publication publication =
            createPublication(
                PUBLICATION_ID,
                status
            );

        RecordingStatusRepository statusRepository =
            new RecordingStatusRepository();

        PublicationReadinessService service =
            new PublicationReadinessService(
                publicationId ->
                    Optional.of(
                        publication
                    ),
                statusRepository
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.ensureReady(
                    PUBLICATION_ID
                )
        );

        assertEquals(
            status,
            publication.status()
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
                    "B0RDY19001"
                ),
                "Produto para liberação automática",
                null,
                "https://www.amazon.com.br/dp/B0RDY19001"
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                2L,
                product,
                OffsetDateTime.parse(
                    "2026-09-30T18:00:00-03:00"
                ),
                Money.of(
                    "99.90"
                ),
                Money.of(
                    "129.90"
                ),
                null,
                null,
                4.8,
                1000L,
                "Amazon.com.br",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "publication-readiness-test",
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
                    "2026-09-30T18:05:00-03:00"
                )
            );

        return new Publication(
            publicationId,
            evaluation,
            "AMAZON_PUBLICATION_V2",
            "AMAZON_COMMERCIAL_PRESENTATION_V2",
            "AMAZON_AFFILIATE_LINK_V2",
            """
            🔹**Produto para liberação automática**
            💰 De ~~R$ 129,90~~ por **R$ 99,90**!
            👇 Tá em Promo!
            🔗 https://www.amazon.com.br/dp/B0RDY19001?tag=test-20
            """
                .strip(),
            "https://www.amazon.com.br/dp/B0RDY19001?tag=test-20",
            status,
            OffsetDateTime.parse(
                "2026-09-30T18:10:00-03:00"
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
