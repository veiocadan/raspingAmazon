package com.raspingamazon.infrastructure.diagnostic;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonPaymentConditionParser;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageEnrichmentClient;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageParser;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AmazonRenderedProductEnrichmentExternalProbeIT {

    private static final String PRODUCT_URL_PROPERTY =
        "amazon.probe.product-url";

    @Test
    void shouldEnrichProductUsingRenderedAmazonPage()
        throws Exception {

        URI productUri =
            configuredProductUri();

        String asin =
            extractAsin(
                productUri
            );

        /*
         * O objetivo deste probe é exclusivamente exercitar a fronteira
         * de enrichment da página individual.
         *
         * Portanto somente ASIN e productUrl são fatos necessários para
         * a chamada externa. Os demais campos de Deals permanecem
         * ausentes, sem inventar valores comerciais.
         */
        ParsedDeal parsedDeal =
            new ParsedDeal(
                asin,
                productUri.toString(),
                "External probe product",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                "EXTERNAL_PROBE"
            );

        try (PlaywrightRenderedProductPageContentProvider provider =
                 new PlaywrightRenderedProductPageContentProvider()) {

            AmazonProductPageEnrichmentClient enrichmentClient =
                new AmazonProductPageEnrichmentClient(
                    provider,
                    new AmazonProductPageParser(),
                    new AmazonPaymentConditionParser()
                );

            ProductEnrichmentResult result =
                enrichmentClient.enrich(
                    parsedDeal
                );

            assertNotNull(
                result
            );

            assertNotNull(
                result.sellerEvidence()
            );

            assertNotNull(
                result.deliveryEvidence()
            );

            assertNotNull(
                result.ratingEvidence()
            );

            assertNotNull(
                result.reviewCountEvidence()
            );

            assertNotNull(
                result.paymentConditions()
            );

            System.out.println();
            System.out.println(
                "AMAZON RENDERED PRODUCT ENRICHMENT PROBE"
            );
            System.out.println(
                "========================================"
            );

            System.out.println(
                "ASIN: "
                    + result.asin()
            );

            System.out.println(
                "Product URL: "
                    + result.productUrl()
            );

            System.out.println();

            System.out.println(
                "Seller raw: "
                    + printable(
                    result.sellerEvidence()
                        .rawValue()
                )
            );

            System.out.println(
                "Seller type: "
                    + result.sellerEvidence()
                    .sellerType()
            );

            System.out.println(
                "Seller source: "
                    + printable(
                    result.sellerEvidence()
                        .source()
                )
            );

            System.out.println();

            System.out.println(
                "Delivery raw: "
                    + printable(
                    result.deliveryEvidence()
                        .rawValue()
                )
            );

            System.out.println(
                "Delivery type: "
                    + result.deliveryEvidence()
                    .deliveryType()
            );

            System.out.println(
                "Delivery source: "
                    + printable(
                    result.deliveryEvidence()
                        .source()
                )
            );

            System.out.println();

            System.out.println(
                "Rating raw: "
                    + printable(
                    result.ratingEvidence()
                        .rawValue()
                )
            );

            System.out.println(
                "Rating normalized: "
                    + valueOrAbsent(
                    result.ratingEvidence()
                        .rating()
                )
            );

            System.out.println(
                "Review count raw: "
                    + printable(
                    result.reviewCountEvidence()
                        .rawValue()
                )
            );

            System.out.println(
                "Review count normalized: "
                    + valueOrAbsent(
                    result.reviewCountEvidence()
                        .reviewCount()
                )
            );

            System.out.println(
                "Payment conditions: "
                    + result.paymentConditions()
                    .size()
            );

            System.out.println(
                "Source: "
                    + result.source()
            );

            System.out.println(
                "Enriched at: "
                    + result.enrichedAt()
            );

            /*
             * Os ASINs utilizados nesta investigação foram inspecionados
             * manualmente e exibem a oferta principal vendida e enviada
             * pela Amazon.
             *
             * Portanto UNKNOWN não é aceitável neste experimento.
             */
            assertEquals(
                SellerType.AMAZON,
                result.sellerEvidence()
                    .sellerType()
            );

            assertEquals(
                DeliveryType.AMAZON,
                result.deliveryEvidence()
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
            "http"
        )
            && !scheme.equalsIgnoreCase(
            "https"
        ))) {

            throw new IllegalStateException(
                "Product URL must use http or https"
            );
        }

        return uri;
    }

    private String extractAsin(
        URI productUri
    ) {

        String path =
            productUri.getPath();

        if (path == null
            || path.isBlank()) {

            throw new IllegalStateException(
                "Product URL does not contain a path"
            );
        }

        String[] parts =
            path.split(
                "/"
            );

        for (int index = 0;
             index < parts.length - 1;
             index++) {

            if (!parts[index].equalsIgnoreCase(
                "dp"
            )
                && !parts[index].equalsIgnoreCase(
                "product"
            )) {

                continue;
            }

            String asin =
                parts[index + 1]
                    .trim()
                    .toUpperCase();

            if (asin.matches(
                "[A-Z0-9]{10}"
            )) {

                return asin;
            }
        }

        throw new IllegalStateException(
            "Could not extract ASIN from product URL: "
                + productUri
        );
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

    private String valueOrAbsent(
        Object value
    ) {

        if (value == null) {
            return "<absent>";
        }

        return value.toString();
    }
}
