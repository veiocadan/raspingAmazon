package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.DeliveryEvidence;
import com.raspingamazon.application.enrichment.contract.SellerEvidence;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testa a extração estrutural de evidências de seller e delivery.
 *
 * <p>As fixtures contêm somente estruturas pequenas da página de produto,
 * enquanto cenários de isolamento estrutural são expressos diretamente
 * nos próprios testes.</p>
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

    @Test
    void shouldExtractVisibleSellerTextWithoutNestedHtmlMarkup() {

        String html =
            """
            <html>
            <body>
                <div
                    id="merchantInfoFeature_feature_div"
                    data-feature-name="merchantInfoFeature">
                    <span>Vendido por</span>
                    <span class="offer-display-feature-text-message">
                        <a href="/sp?seller=acer">
                            Acer <strong>Brasil</strong>
                        </a>
                    </span>
                </div>

                <div
                    id="fulfillerInfoFeature_feature_div"
                    data-feature-name="fulfillerInfoFeature">
                    <span>Enviado por</span>
                    <span class="offer-display-feature-text-message">
                        Amazon
                    </span>
                </div>
            </body>
            </html>
            """;

        AmazonProductPageParser.ParsedProductOffer result =
            parser.parse(
                html
            );

        assertEquals(
            "Acer Brasil",
            result.sellerEvidence()
                .rawValue()
        );

        assertEquals(
            SellerType.THIRD_PARTY,
            result.sellerEvidence()
                .sellerType()
        );

        assertEquals(
            "Amazon",
            result.deliveryEvidence()
                .rawValue()
        );

        assertEquals(
            DeliveryType.AMAZON,
            result.deliveryEvidence()
                .deliveryType()
        );
    }

    @Test
    void shouldExtractVisibleDeliveryTextWithoutNestedHtmlMarkup() {

        String html =
            """
            <html>
            <body>
                <div
                    id="merchantInfoFeature_feature_div"
                    data-feature-name="merchantInfoFeature">
                    <span>Vendido por</span>
                    <span class="offer-display-feature-text-message">
                        Samsung Loja Oficial
                    </span>
                </div>

                <div
                    id="fulfillerInfoFeature_feature_div"
                    data-feature-name="fulfillerInfoFeature">
                    <span>Enviado por</span>
                    <span class="offer-display-feature-text-message">
                        <a href="/sp?seller=samsung">
                            Samsung <strong>Loja Oficial</strong>
                        </a>
                    </span>
                </div>
            </body>
            </html>
            """;

        AmazonProductPageParser.ParsedProductOffer result =
            parser.parse(
                html
            );

        assertEquals(
            "Samsung Loja Oficial",
            result.deliveryEvidence()
                .rawValue()
        );

        assertEquals(
            DeliveryType.THIRD_PARTY,
            result.deliveryEvidence()
                .deliveryType()
        );
    }

    @Test
    void shouldNotLeakSellerFromAFeatureThatComesAfterMerchantFeature() {

        String html =
            """
            <html>
            <body>
                <div
                    id="merchantInfoFeature_feature_div"
                    data-feature-name="merchantInfoFeature">
                    <span>Informação indisponível</span>
                </div>

                <div data-feature-name="secondaryOffer">
                    <span>Vendido por</span>
                    <span class="offer-display-feature-text-message">
                        Amazon.com.br
                    </span>
                </div>
            </body>
            </html>
            """;

        AmazonProductPageParser.ParsedProductOffer result =
            parser.parse(
                html
            );

        assertNull(
            result.sellerEvidence()
                .rawValue()
        );

        assertEquals(
            SellerType.UNKNOWN,
            result.sellerEvidence()
                .sellerType()
        );

        assertNull(
            result.sellerEvidence()
                .source()
        );
    }

    @Test
    void shouldNotLeakDeliveryFromAFeatureThatComesAfterFulfillerFeature() {

        String html =
            """
            <html>
            <body>
                <div
                    id="merchantInfoFeature_feature_div"
                    data-feature-name="merchantInfoFeature">
                    <span>Vendido por</span>
                    <span class="offer-display-feature-text-message">
                        Loja Terceira
                    </span>
                </div>

                <div
                    id="fulfillerInfoFeature_feature_div"
                    data-feature-name="fulfillerInfoFeature">
                    <span>Informação indisponível</span>
                </div>

                <div data-feature-name="secondaryOffer">
                    <span>Enviado por</span>
                    <span class="offer-display-feature-text-message">
                        Amazon
                    </span>
                </div>
            </body>
            </html>
            """;

        AmazonProductPageParser.ParsedProductOffer result =
            parser.parse(
                html
            );

        assertNull(
            result.deliveryEvidence()
                .rawValue()
        );

        assertEquals(
            DeliveryType.UNKNOWN,
            result.deliveryEvidence()
                .deliveryType()
        );

        assertNull(
            result.deliveryEvidence()
                .source()
        );
    }

    @Test
    void shouldPreferCanonicalMerchantFeatureOverLaterDuplicateFeature() {

        String html =
            """
            <html>
            <body>
                <div
                    id="merchantInfoFeature_feature_div"
                    data-feature-name="merchantInfoFeature">
                    <span>Vendido por</span>
                </div>

                <div data-feature-name="merchantInfoFeature">
                    <span>Vendido por</span>
                    <span class="offer-display-feature-text-message">
                        Amazon.com.br
                    </span>
                </div>
            </body>
            </html>
            """;

        AmazonProductPageParser.ParsedProductOffer result =
            parser.parse(
                html
            );

        assertNull(
            result.sellerEvidence()
                .rawValue()
        );

        assertEquals(
            SellerType.UNKNOWN,
            result.sellerEvidence()
                .sellerType()
        );
    }

    @Test
    void shouldUseCombinedMerchantFeatureForSellerAndDelivery() {

        String html =
            """
            <html>
            <body>
                <div
                    id="merchantInfoFeature_feature_div"
                    data-feature-name="merchantInfoFeature">
                    <span>Enviado / Vendido</span>
                    <span class="offer-display-feature-text-message">
                        <a>Amazon.com.br</a>
                    </span>
                </div>
            </body>
            </html>
            """;

        AmazonProductPageParser.ParsedProductOffer result =
            parser.parse(
                html
            );

        assertEquals(
            "Amazon.com.br",
            result.sellerEvidence()
                .rawValue()
        );

        assertEquals(
            SellerType.AMAZON,
            result.sellerEvidence()
                .sellerType()
        );

        assertEquals(
            "merchantInfoFeature",
            result.sellerEvidence()
                .source()
        );

        assertEquals(
            "Amazon",
            result.deliveryEvidence()
                .rawValue()
        );

        assertEquals(
            DeliveryType.AMAZON,
            result.deliveryEvidence()
                .deliveryType()
        );

        assertEquals(
            "merchantInfoFeature",
            result.deliveryEvidence()
                .source()
        );
    }

    @Test
    void shouldKeepSellerAndDeliveryUnknownWhenFeatureBlocksAreAbsent() {

        String html =
            """
            <html>
            <body>
                <div data-feature-name="unrelatedFeature">
                    <span>Vendido por</span>
                    <span class="offer-display-feature-text-message">
                        Amazon.com.br
                    </span>

                    <span>Enviado por</span>
                    <span class="offer-display-feature-text-message">
                        Amazon
                    </span>
                </div>
            </body>
            </html>
            """;

        AmazonProductPageParser.ParsedProductOffer result =
            parser.parse(
                html
            );

        assertNull(
            result.sellerEvidence()
                .rawValue()
        );

        assertEquals(
            SellerType.UNKNOWN,
            result.sellerEvidence()
                .sellerType()
        );

        assertNull(
            result.deliveryEvidence()
                .rawValue()
        );

        assertEquals(
            DeliveryType.UNKNOWN,
            result.deliveryEvidence()
                .deliveryType()
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
