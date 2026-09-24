package com.raspingamazon.application.publication.affiliate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmazonAffiliateLinkGeneratorV2Test {

    private static final String ASSOCIATE_TAG =
        "teste-20";

    private final AmazonAffiliateLinkGeneratorV2 generator =
        new AmazonAffiliateLinkGeneratorV2(
            ASSOCIATE_TAG
        );

    @Test
    void shouldExposeGeneratorVersion() {

        assertEquals(
            "AMAZON_AFFILIATE_LINK_V2",
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
            AmazonAffiliateLinkGeneratorV2.VERSION,
            result.generatorVersion()
        );

        assertEquals(
            "https://www.amazon.com.br/dp/B0PUB13004?tag=teste-20",
            result.url()
        );
    }

    @Test
    void shouldPreserveAlreadyPercentEncodedProductPath() {

        AffiliateLink result =
            generator.generate(
                "https://www.amazon.com.br/"
                    + "Celular-Samsung-Recursos-"
                    + "Atualiza%C3%A7%C3%B5es-"
                    + "Seguran%C3%A7a/dp/B0GVT7QXF7"
            );

        assertEquals(
            "https://www.amazon.com.br/"
                + "Celular-Samsung-Recursos-"
                + "Atualiza%C3%A7%C3%B5es-"
                + "Seguran%C3%A7a/dp/B0GVT7QXF7"
                + "?tag=teste-20",
            result.url()
        );
    }

    @Test
    void shouldEncodeUnicodeProductPathExactlyOnce() {

        AffiliateLink result =
            generator.generate(
                "https://www.amazon.com.br/"
                    + "Celular-Atualizações-Segurança/"
                    + "dp/B0GVT7QXF7"
            );

        assertEquals(
            "https://www.amazon.com.br/"
                + "Celular-Atualiza%C3%A7%C3%B5es-"
                + "Seguran%C3%A7a/dp/B0GVT7QXF7"
                + "?tag=teste-20",
            result.url()
        );
    }

    @Test
    void shouldRemovePreviousQueryParametersBeforeGeneratingLink() {

        AffiliateLink result =
            generator.generate(
                "https://www.amazon.com.br/"
                    + "Atualiza%C3%A7%C3%B5es/dp/B0PUB13004"
                    + "?ref_=abc&tag=old-20"
            );

        assertEquals(
            "https://www.amazon.com.br/"
                + "Atualiza%C3%A7%C3%B5es/dp/B0PUB13004"
                + "?tag=teste-20",
            result.url()
        );
    }

    @Test
    void shouldRemoveFragmentBeforeGeneratingLink() {

        AffiliateLink result =
            generator.generate(
                "https://www.amazon.com.br/"
                    + "Atualiza%C3%A7%C3%B5es/dp/B0PUB13004"
                    + "#customerReviews"
            );

        assertEquals(
            "https://www.amazon.com.br/"
                + "Atualiza%C3%A7%C3%B5es/dp/B0PUB13004"
                + "?tag=teste-20",
            result.url()
        );
    }

    @Test
    void shouldEncodeAssociateTagWithoutReencodingProductPath() {

        AmazonAffiliateLinkGeneratorV2 generatorWithSpecialTag =
            new AmazonAffiliateLinkGeneratorV2(
                "teste especial-20"
            );

        AffiliateLink result =
            generatorWithSpecialTag.generate(
                "https://www.amazon.com.br/"
                    + "Atualiza%C3%A7%C3%B5es/dp/B0PUB13004"
            );

        assertEquals(
            "https://www.amazon.com.br/"
                + "Atualiza%C3%A7%C3%B5es/dp/B0PUB13004"
                + "?tag=teste+especial-20",
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
            () -> new AmazonAffiliateLinkGeneratorV2(
                " "
            )
        );
    }
}
