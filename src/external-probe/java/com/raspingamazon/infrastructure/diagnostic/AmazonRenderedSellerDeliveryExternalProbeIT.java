package com.raspingamazon.infrastructure.diagnostic;

import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageParser;
import com.raspingamazon.infrastructure.amazon.enrichment.ProductPageContent;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AmazonRenderedSellerDeliveryExternalProbeIT {

    private static final String PRODUCT_URL_PROPERTY =
        "amazon.probe.product-url";

    @Test
    void shouldParseSellerAndDeliveryFromRenderedProductPage()
        throws Exception {

        URI productUri =
            configuredProductUri();

        try (PlaywrightRenderedProductPageContentProvider provider =
                 new PlaywrightRenderedProductPageContentProvider()) {

            ProductPageContent content =
                provider.load(
                    productUri
                );

            AmazonProductPageParser parser =
                new AmazonProductPageParser();

            AmazonProductPageParser.ParsedProductOffer parsed =
                parser.parse(
                    content.html()
                );

            assertNotNull(
                parsed.sellerEvidence()
            );

            assertNotNull(
                parsed.deliveryEvidence()
            );

            System.out.println();
            System.out.println(
                "AMAZON RENDERED SELLER / DELIVERY PROBE"
            );
            System.out.println(
                "======================================"
            );

            System.out.println(
                "Requested URI: "
                    + productUri
            );

            System.out.println(
                "Resolved URI: "
                    + content.resolvedUri()
            );

            System.out.println();

            System.out.println(
                "Seller raw: "
                    + printable(
                    parsed.sellerEvidence()
                        .rawValue()
                )
            );

            System.out.println(
                "Seller type: "
                    + parsed.sellerEvidence()
                    .sellerType()
            );

            System.out.println(
                "Seller source: "
                    + printable(
                    parsed.sellerEvidence()
                        .source()
                )
            );

            System.out.println();

            System.out.println(
                "Delivery raw: "
                    + printable(
                    parsed.deliveryEvidence()
                        .rawValue()
                )
            );

            System.out.println(
                "Delivery type: "
                    + parsed.deliveryEvidence()
                    .deliveryType()
            );

            System.out.println(
                "Delivery source: "
                    + printable(
                    parsed.deliveryEvidence()
                        .source()
                )
            );

            /*
             * Estes três ASINs foram inspecionados manualmente e
             * apresentaram a oferta principal enviada/vendida pela
             * Amazon.
             *
             * Portanto, nesta investigação específica, UNKNOWN não é
             * um resultado aceitável.
             */
            assertEquals(
                com.raspingamazon.domain.validation.SellerType.AMAZON,
                parsed.sellerEvidence()
                    .sellerType()
            );

            assertEquals(
                com.raspingamazon.domain.validation.DeliveryType.AMAZON,
                parsed.deliveryEvidence()
                    .deliveryType()
            );
        }
    }

    private URI configuredProductUri() {

        String configured =
            System.getProperty(
                PRODUCT_URL_PROPERTY
            );

        if (configured == null
            || configured.isBlank()) {

            throw new IllegalStateException(
                "External probe requires -D"
                    + PRODUCT_URL_PROPERTY
                    + "=https://www.amazon.com.br/dp/ASIN"
            );
        }

        URI uri;

        try {

            uri =
                URI.create(
                    configured.trim()
                );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                "Invalid product URL supplied in -D"
                    + PRODUCT_URL_PROPERTY,
                exception
            );
        }

        String scheme =
            uri.getScheme();

        if (scheme == null
            || (!scheme.equalsIgnoreCase(
            "https"
        )
            && !scheme.equalsIgnoreCase(
            "http"
        ))) {

            throw new IllegalStateException(
                "Product URL must use http or https"
            );
        }

        return uri;
    }

    private String printable(
        String value
    ) {

        if (value == null) {
            return "<null>";
        }

        if (value.isBlank()) {
            return "<blank>";
        }

        return value;
    }
}
