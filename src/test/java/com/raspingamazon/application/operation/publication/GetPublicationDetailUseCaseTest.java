package com.raspingamazon.application.operation.publication;

import com.raspingamazon.application.operation.publication.port.PublicationOperationalDetailQueryPort;
import com.raspingamazon.domain.product.Asin;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetPublicationDetailUseCaseTest {

    private static final OffsetDateTime CREATED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:00:00-03:00"
        );

    @Test
    void shouldDelegatePublicationIdToQueryPort() {

        PublicationDetail expected =
            detail();

        AtomicLong receivedId =
            new AtomicLong();

        PublicationOperationalDetailQueryPort queryPort =
            publicationId -> {

                receivedId.set(
                    publicationId
                );

                return Optional.of(
                    expected
                );
            };

        GetPublicationDetailUseCase useCase =
            new GetPublicationDetailUseCase(
                queryPort
            );

        PublicationDetail actual =
            useCase.execute(
                    30L
                )
                .orElseThrow();

        assertSame(
            expected,
            actual
        );

        assertEquals(
            30L,
            receivedId.get()
        );
    }

    @Test
    void shouldPreserveEmptyResult() {

        GetPublicationDetailUseCase useCase =
            new GetPublicationDetailUseCase(
                publicationId -> Optional.empty()
            );

        assertTrue(
            useCase.execute(
                    30L
                )
                .isEmpty()
        );
    }

    @Test
    void shouldRejectNonPositivePublicationId() {

        GetPublicationDetailUseCase useCase =
            new GetPublicationDetailUseCase(
                publicationId -> Optional.empty()
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                0L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                -1L
            )
        );
    }

    @Test
    void shouldRejectNullQueryPort() {

        assertThrows(
            NullPointerException.class,
            () -> new GetPublicationDetailUseCase(
                null
            )
        );
    }

    private PublicationDetail detail() {

        PublicationSummary summary =
            new PublicationSummary(
                30L,
                20L,
                10L,
                new Asin(
                    "B0PUB14022"
                ),
                "Produto publicação",
                "CREATED",
                "TEMPLATE_V1",
                "COMMERCIAL_PRESENTATION_V1",
                "AFFILIATE_LINK_V1",
                CREATED_AT
            );

        return new PublicationDetail(
            summary,
            "Texto completo",
            "https://www.amazon.com.br/dp/B0PUB14022?tag=test"
        );
    }
}
