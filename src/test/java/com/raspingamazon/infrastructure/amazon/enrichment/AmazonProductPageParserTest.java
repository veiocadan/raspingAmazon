package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testa a extração de evidências de seller e delivery
 * usando fixtures mínimas.
 *
 * <p>As fixtures contêm apenas as estruturas utilizadas pelo parser,
 * evitando depender de páginas Amazon completas de vários megabytes.</p>
 */
class AmazonProductPageParserTest {

    private final AmazonProductPageParser parser =
        new AmazonProductPageParser();

    @Test
    void shouldRejectNullHtml() {

        assertThrows(
            NullPointerException.class,
            () -> parser.parse(
                null
            )
        );
    }

    @Test
    void shouldRejectBlankHtml() {

        assertThrows(
            IllegalArgumentException.class,
            () -> parser.parse(
                "   "
            )
        );
    }

    @Test
    void shouldParseAmazonBrazilOffer()
        throws Exception {

        String html =
            loadFixture(
                "amazon-amazon.html"
            );

        AmazonProductPageParser.ParsedProductOffer result =
            parser.parse(
                html
            );

        assertNotNull(
            result
        );

        SellerEvidence sellerEvidence =
            result.sellerEvidence();

        DeliveryEvidence deliveryEvidence =
            result.deliveryEvidence();

        assertNotNull(
            sellerEvidence
        );

        assertNotNull(
            deliveryEvidence
        );

        assertEquals(
            "Amazon.com.br",
            sellerEvidence.rawValue()
        );

        assertEquals(
            SellerType.AMAZON,
            sellerEvidence.sellerType()
        );

        assertEquals(
            "merchantInfoFeature",
            sellerEvidence.source()
        );

        assertEquals(
            "Amazon",
            deliveryEvidence.rawValue()
        );

        assertEquals(
            DeliveryType.AMAZON,
            deliveryEvidence.deliveryType()
        );

        assertEquals(
            "fulfillerInfoFeature",
            deliveryEvidence.source()
        );
    }

    @Test
    void shouldParseAmazonGlobalOffer()
        throws Exception {

        String html =
            loadFixture(
                "amazon-global.html"
            );

        AmazonProductPageParser.ParsedProductOffer result =
            parser.parse(
                html
            );

        SellerEvidence sellerEvidence =
            result.sellerEvidence();

        DeliveryEvidence deliveryEvidence =
            result.deliveryEvidence();

        assertEquals(
            "Amazon Global",
            sellerEvidence.rawValue()
        );

        assertEquals(
            SellerType.AMAZON,
            sellerEvidence.sellerType()
        );

        assertEquals(
            "merchantInfoFeature",
            sellerEvidence.source()
        );

        assertEquals(
            "Amazon",
            deliveryEvidence.rawValue()
        );

        assertEquals(
            DeliveryType.AMAZON,
            deliveryEvidence.deliveryType()
        );

        assertEquals(
            "fulfillerInfoFeature",
            deliveryEvidence.source()
        );
    }

    @Test
    void shouldParseThirdPartySellerWithAmazonDelivery()
        throws Exception {

        String html =
            loadFixture(
                "thirdparty-amazon.html"
            );

        AmazonProductPageParser.ParsedProductOffer result =
            parser.parse(
                html
            );

        SellerEvidence sellerEvidence =
            result.sellerEvidence();

        DeliveryEvidence deliveryEvidence =
            result.deliveryEvidence();

        assertEquals(
            "Imagem Hitech FULL",
            sellerEvidence.rawValue()
        );

        assertEquals(
            SellerType.THIRD_PARTY,
            sellerEvidence.sellerType()
        );

        assertEquals(
            "merchantInfoFeature",
            sellerEvidence.source()
        );

        assertEquals(
            "Amazon",
            deliveryEvidence.rawValue()
        );

        assertEquals(
            DeliveryType.AMAZON,
            deliveryEvidence.deliveryType()
        );

        assertEquals(
            "fulfillerInfoFeature",
            deliveryEvidence.source()
        );
    }

    @Test
    void shouldParseThirdPartySellerAndDelivery()
        throws Exception {

        String html =
            loadFixture(
                "thirdparty-thirdparty.html"
            );

        AmazonProductPageParser.ParsedProductOffer result =
            parser.parse(
                html
            );

        SellerEvidence sellerEvidence =
            result.sellerEvidence();

        DeliveryEvidence deliveryEvidence =
            result.deliveryEvidence();

        assertEquals(
            "BOYA DO BRASIL",
            sellerEvidence.rawValue()
        );

        assertEquals(
            SellerType.THIRD_PARTY,
            sellerEvidence.sellerType()
        );

        assertEquals(
            "merchantInfoFeature",
            sellerEvidence.source()
        );

        assertEquals(
            "BOYA DO BRASIL",
            deliveryEvidence.rawValue()
        );

        assertEquals(
            DeliveryType.THIRD_PARTY,
            deliveryEvidence.deliveryType()
        );

        assertEquals(
            "fulfillerInfoFeature",
            deliveryEvidence.source()
        );
    }

    /**
     * Carrega somente fixtures pequenas específicas de página de produto.
     */
    private String loadFixture(
        String fileName
    ) throws Exception {

        String resourcePath =
            "/amazon/fixtures/product/"
                + fileName;

        try (var inputStream =
                 getClass()
                     .getResourceAsStream(
                         resourcePath
                     )) {

            if (inputStream == null) {
                throw new IllegalStateException(
                    "Fixture not found: "
                        + resourcePath
                );
            }

            return new String(
                inputStream.readAllBytes(),
                StandardCharsets.UTF_8
            );
        }
    }
}
