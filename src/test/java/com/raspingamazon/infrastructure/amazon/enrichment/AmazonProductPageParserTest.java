package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes do parser da página individual de produto.
 *
 * <p>Além dos valores normalizados, estes testes verificam a origem
 * real de cada evidência. Isso é importante para a auditabilidade:
 * não basta saber que a entrega foi classificada como Amazon; também
 * precisamos saber de qual estrutura da página essa conclusão veio.</p>
 */
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
    void shouldParseAmazonBrazilOffer()
            throws Exception {

        String html =
                loadFixture(
                        "totalamazon.html"
                );

        AmazonProductPageParser.ParsedProductOffer result =
                parser.parse(
                        html
                );

        /*
         * Seller foi encontrado na estrutura de merchant.
         */
        assertEquals(
                "Amazon.com.br",
                result.sellerEvidence().rawValue()
        );

        assertEquals(
                SellerType.AMAZON,
                result.sellerEvidence().sellerType()
        );

        assertEquals(
                "merchantInfoFeature",
                result.sellerEvidence().source()
        );

        /*
         * Delivery foi normalizado para Amazon.
         */
        assertEquals(
                "Amazon",
                result.deliveryEvidence().rawValue()
        );

        assertEquals(
                DeliveryType.AMAZON,
                result.deliveryEvidence().deliveryType()
        );

        /*
         * Atenção:
         *
         * Nesta fixture específica, a evidência de entrega foi obtida
         * pelo fallback da estrutura combinada merchantInfoFeature.
         *
         * Não devemos substituir essa origem por fulfillerInfoFeature,
         * pois isso falsificaria a provenance da informação.
         */
        assertEquals(
                "merchantInfoFeature",
                result.deliveryEvidence().source()
        );
    }

    @Test
    void shouldParseAmazonGlobalOffer()
            throws Exception {

        String html =
                loadFixture(
                        "amazon-amazonglobal.html"
                );

        AmazonProductPageParser.ParsedProductOffer result =
                parser.parse(
                        html
                );

        assertEquals(
                "Amazon Global",
                result.sellerEvidence().rawValue()
        );

        assertEquals(
                SellerType.AMAZON,
                result.sellerEvidence().sellerType()
        );

        assertEquals(
                "merchantInfoFeature",
                result.sellerEvidence().source()
        );

        assertEquals(
                "Amazon",
                result.deliveryEvidence().rawValue()
        );

        assertEquals(
                DeliveryType.AMAZON,
                result.deliveryEvidence().deliveryType()
        );
    }

    @Test
    void shouldParseThirdPartySellerWithAmazonDelivery()
            throws Exception {

        String html =
                loadFixture(
                        "misto.html"
                );

        AmazonProductPageParser.ParsedProductOffer result =
                parser.parse(
                        html
                );

        assertEquals(
                "Imagem Hitech FULL",
                result.sellerEvidence().rawValue()
        );

        assertEquals(
                SellerType.THIRD_PARTY,
                result.sellerEvidence().sellerType()
        );

        assertEquals(
                "merchantInfoFeature",
                result.sellerEvidence().source()
        );

        assertEquals(
                "Amazon",
                result.deliveryEvidence().rawValue()
        );

        assertEquals(
                DeliveryType.AMAZON,
                result.deliveryEvidence().deliveryType()
        );

        assertEquals(
                "fulfillerInfoFeature",
                result.deliveryEvidence().source()
        );
    }

    @Test
    void shouldParseThirdPartySellerAndDelivery()
            throws Exception {

        String html =
                loadFixture(
                        "totalterceiro.html"
                );

        AmazonProductPageParser.ParsedProductOffer result =
                parser.parse(
                        html
                );

        assertEquals(
                "BOYA DO BRASIL",
                result.sellerEvidence().rawValue()
        );

        assertEquals(
                SellerType.THIRD_PARTY,
                result.sellerEvidence().sellerType()
        );

        assertEquals(
                "BOYA DO BRASIL",
                result.deliveryEvidence().rawValue()
        );

        assertEquals(
                DeliveryType.THIRD_PARTY,
                result.deliveryEvidence().deliveryType()
        );
    }

    /**
     * Carrega uma fixture HTML existente na suíte.
     *
     * @param fileName nome do arquivo dentro de src/test/resources/amazon
     * @return conteúdo textual da fixture
     */
    private String loadFixture(
            String fileName
    ) throws Exception {

        try (var inputStream =
                     getClass()
                             .getResourceAsStream(
                                     "/amazon/" + fileName
                             )) {

            if (inputStream == null) {
                throw new IllegalStateException(
                        "Fixture not found: "
                                + fileName
                );
            }

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}