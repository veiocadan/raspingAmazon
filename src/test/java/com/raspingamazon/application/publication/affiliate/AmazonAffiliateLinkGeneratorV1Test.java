package com.raspingamazon.application.publication.affiliate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmazonAffiliateLinkGeneratorV1Test {

    private static final String ASSOCIATE_TAG =
        "teste-20";

    private final AmazonAffiliateLinkGeneratorV1 generator =
        new AmazonAffiliateLinkGeneratorV1(
            ASSOCIATE_TAG
        );

    @Test
    void shouldExposeGeneratorVersion() {

        assertEquals(
            "AMAZON_AFFILIATE_LINK_V1",
            generator.version()
        );
    }

    @Test
    void shouldGenerateAffiliateLinkFromCleanProductUrl() {

        AffiliateLink result =
            generator.generate(
                "https://www.amazon.com.br/dp/B0PUB13004"
            );

        assertEquals(
            AmazonAffiliateLinkGeneratorV1.VERSION,
            result.generatorVersion()
        );

        assertEquals(
            "https://www.amazon.com.br/dp/B0PUB13004?tag=teste-20",
            result.url()
        );
    }

    @Test
    void shouldRemovePreviousQueryParametersBeforeGeneratingLink() {

        AffiliateLink result =
            generator.generate(
                "https://www.amazon.com.br/dp/B0PUB13004"
                    + "?ref_=abc&tag=old-20"
            );

        assertEquals(
            "https://www.amazon.com.br/dp/B0PUB13004?tag=teste-20",
            result.url()
        );
    }

    @Test
    void shouldRemoveFragmentBeforeGeneratingLink() {

        AffiliateLink result =
            generator.generate(
                "https://www.amazon.com.br/dp/B0PUB13004#customerReviews"
            );

        assertEquals(
            "https://www.amazon.com.br/dp/B0PUB13004?tag=teste-20",
            result.url()
        );
    }

    @Test
    void shouldRejectNonHttpsUrl() {

        assertThrows(
            IllegalArgumentException.class,
            () -> generator.generate(
                "http://www.amazon.com.br/dp/B0PUB13004"
            )
        );
    }

    @Test
    void shouldRejectForeignAmazonHost() {

        assertThrows(
            IllegalArgumentException.class,
            () -> generator.generate(
                "https://www.amazon.com/dp/B0PUB13004"
            )
        );
    }

    @Test
    void shouldRejectNonAmazonHost() {

        assertThrows(
            IllegalArgumentException.class,
            () -> generator.generate(
                "https://example.com/dp/B0PUB13004"
            )
        );
    }

    @Test
    void shouldRejectBlankProductUrl() {

        assertThrows(
            IllegalArgumentException.class,
            () -> generator.generate(
                " "
            )
        );
    }

    @Test
    void shouldRejectBlankAssociateTag() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new AmazonAffiliateLinkGeneratorV1(
                " "
            )
        );
    }
}
