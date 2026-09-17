package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmazonProductPageParserTest {

    private final AmazonProductPageParser parser =
            new AmazonProductPageParser();

    @Test
    void shouldRejectNullHtml() {
        assertThrows(
                NullPointerException.class,
                () -> parser.parse(null)
        );
    }

    @Test
    void shouldRejectBlankHtml() {
        assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("   ")
        );
    }

    @Test
    void shouldParseAmazonBrazilOffer() throws Exception {
        String html = loadFixture("totalamazon.html");

        AmazonProductPageParser.ParsedProductOffer result =
                parser.parse(html);

        assertEquals(
                "Amazon.com.br",
                result.rawSellerValue()
        );

        assertEquals(
                SellerType.AMAZON,
                result.sellerType()
        );

        assertEquals(
                "Amazon",
                result.rawDeliveryValue()
        );

        assertEquals(
                DeliveryType.AMAZON,
                result.deliveryType()
        );
    }

    @Test
    void shouldParseAmazonGlobalOffer() throws Exception {
        String html = loadFixture("amazon-amazonglobal.html");

        AmazonProductPageParser.ParsedProductOffer result =
                parser.parse(html);

        assertEquals(
                "Amazon Global",
                result.rawSellerValue()
        );

        assertEquals(
                SellerType.AMAZON,
                result.sellerType()
        );

        assertEquals(
                "Amazon",
                result.rawDeliveryValue()
        );

        assertEquals(
                DeliveryType.AMAZON,
                result.deliveryType()
        );
    }

    @Test
    void shouldParseThirdPartySellerWithAmazonDelivery() throws Exception {
        String html = loadFixture("misto.html");

        AmazonProductPageParser.ParsedProductOffer result =
                parser.parse(html);

        assertEquals(
                "Imagem Hitech FULL",
                result.rawSellerValue()
        );

        assertEquals(
                SellerType.THIRD_PARTY,
                result.sellerType()
        );

        assertEquals(
                "Amazon",
                result.rawDeliveryValue()
        );

        assertEquals(
                DeliveryType.AMAZON,
                result.deliveryType()
        );
    }

    @Test
    void shouldParseThirdPartySellerAndDelivery() throws Exception {
        String html = loadFixture("totalterceiro.html");

        AmazonProductPageParser.ParsedProductOffer result =
                parser.parse(html);

        assertEquals(
                "BOYA DO BRASIL",
                result.rawSellerValue()
        );

        assertEquals(
                SellerType.THIRD_PARTY,
                result.sellerType()
        );

        assertEquals(
                "BOYA DO BRASIL",
                result.rawDeliveryValue()
        );

        assertEquals(
                DeliveryType.THIRD_PARTY,
                result.deliveryType()
        );
    }

    private String loadFixture(String fileName) throws Exception {
        try (var inputStream = getClass()
                .getResourceAsStream("/amazon/" + fileName)) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "Fixture not found: " + fileName
                );
            }

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}