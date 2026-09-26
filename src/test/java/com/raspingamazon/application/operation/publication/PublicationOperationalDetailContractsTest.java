package com.raspingamazon.application.operation.publication;

import com.raspingamazon.domain.product.Asin;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationOperationalDetailContractsTest {

    private static final OffsetDateTime CREATED_AT =
        OffsetDateTime.parse(
            "2026-09-24T20:00:00-03:00"
        );

    @Test
    void shouldCreatePublicationDetail() {

        PublicationDetail detail =
            new PublicationDetail(
                summary(),
                "Texto completo da publicação",
                "https://www.amazon.com.br/dp/B0PUB14021?tag=test"
            );

        assertEquals(
            30L,
            detail.summary()
                .publicationId()
        );

        assertEquals(
            "Texto completo da publicação",
            detail.generatedText()
        );

        assertEquals(
            "https://www.amazon.com.br/dp/B0PUB14021?tag=test",
            detail.affiliateUrl()
        );
    }

    @Test
    void shouldAllowNullHistoricalAffiliateUrl() {

        PublicationDetail detail =
            new PublicationDetail(
                summary(),
                "Texto histórico",
                null
            );

        assertNull(
            detail.affiliateUrl()
        );
    }

    @Test
    void shouldRejectNullSummary() {

        assertThrows(
            NullPointerException.class,
            () -> new PublicationDetail(
                null,
                "Texto válido",
                "https://example.invalid"
            )
        );
    }

    @Test
    void shouldRejectBlankGeneratedText() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new PublicationDetail(
                summary(),
                " ",
                "https://example.invalid"
            )
        );
    }

    private PublicationSummary summary() {

        return new PublicationSummary(
            30L,
            20L,
            10L,
            new Asin(
                "B0PUB14021"
            ),
            "Produto publicação",
            "CREATED",
            "TEMPLATE_V1",
            "COMMERCIAL_PRESENTATION_V1",
            "AFFILIATE_LINK_V1",
            CREATED_AT
        );
    }
}
