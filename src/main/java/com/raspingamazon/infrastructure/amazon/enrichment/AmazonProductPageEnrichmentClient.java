package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public final class AmazonProductPageEnrichmentClient
        implements ProductEnrichmentClient {

    private static final String USER_AGENT =
            "RaspingAmazon/1.0";

    private static final String SOURCE =
            "AMAZON_PRODUCT_PAGE";

    private static final java.time.Duration REQUEST_TIMEOUT =
            java.time.Duration.ofSeconds(20);

    private final HttpClient httpClient;

    private final AmazonProductPageParser parser;

    public AmazonProductPageEnrichmentClient() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(REQUEST_TIMEOUT)
                        .followRedirects(
                                HttpClient.Redirect.NORMAL
                        )
                        .build(),
                new AmazonProductPageParser()
        );
    }

    public AmazonProductPageEnrichmentClient(
            HttpClient httpClient,
            AmazonProductPageParser parser
    ) {
        this.httpClient =
                Objects.requireNonNull(
                        httpClient,
                        "HTTP client must not be null"
                );

        this.parser =
                Objects.requireNonNull(
                        parser,
                        "Parser must not be null"
                );
    }

    @Override
    public ProductEnrichmentResult enrich(
            ParsedDeal parsedDeal
    ) {
        Objects.requireNonNull(
                parsedDeal,
                "Parsed deal must not be null"
        );

        String productUrl = parsedDeal.productUrl();

        if (productUrl == null
                || productUrl.isBlank()) {

            throw new ProductEnrichmentException(
                    "Parsed deal does not contain a product URL"
            );
        }

        URI uri = URI.create(productUrl);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(REQUEST_TIMEOUT)
                        .header(
                                "User-Agent",
                                USER_AGENT
                        )
                        .header(
                                "Accept",
                                "text/html"
                        )
                        .GET()
                        .build();

        HttpResponse<String> response;

        try {
            response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new ProductEnrichmentException(
                    "Product page request was interrupted",
                    exception
            );
        } catch (Exception exception) {
            throw new ProductEnrichmentException(
                    "Failed to retrieve Amazon product page",
                    exception
            );
        }

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            throw new ProductEnrichmentException(
                    "Amazon product page returned HTTP "
                            + response.statusCode()
            );
        }

        String html = response.body();

        if (html == null || html.isBlank()) {
            throw new ProductEnrichmentException(
                    "Amazon product page returned an empty response"
            );
        }

        AmazonProductPageParser.ParsedProductOffer parsed =
                parser.parse(html);

        OffsetDateTime collectedAt =
                OffsetDateTime.now(ZoneOffset.UTC);

        return new ProductEnrichmentResult(
                parsedDeal.asin(),
                parsedDeal.title(),
                parsed.sellerType(),
                parsed.rawSellerValue(),
                parsed.rawDeliveryValue(),
                parsed.deliveryType(),
                SOURCE,
                productUrl,
                collectedAt
        );
    }

    public static final class ProductEnrichmentException
            extends RuntimeException {

        public ProductEnrichmentException(
                String message
        ) {
            super(message);
        }

        public ProductEnrichmentException(
                String message,
                Throwable cause
        ) {
            super(message, cause);
        }
    }
}