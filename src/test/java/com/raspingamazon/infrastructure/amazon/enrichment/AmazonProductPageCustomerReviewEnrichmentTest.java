package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova que rating/reviewCount usam o mesmo HTML já adquirido pelo
 * AmazonProductPageEnrichmentClient.
 */
class AmazonProductPageCustomerReviewEnrichmentTest {

    private static final String ASIN =
        "B0GVT7QXF7";

    @Test
    void shouldTransportCustomerReviewEvidenceFromSameProductPage()
        throws Exception {

        String html =
            loadFixture(
                "amazon-amazon.html"
            )
                + customerReviewBlock();

        try (TestHttpServer server =
                 TestHttpServer.start(
                     html
                 )) {

            AmazonProductPageEnrichmentClient client =
                new AmazonProductPageEnrichmentClient(
                    HttpClient.newHttpClient(),
                    new AmazonProductPageParser()
                );

            ProductEnrichmentResult result =
                client.enrich(
                    parsedDeal(
                        server.url()
                    )
                );

            assertTrue(
                result.ratingEvidence().available()
            );

            assertEquals(
                4.8d,
                result.ratingEvidence().rating()
            );

            assertTrue(
                result.reviewCountEvidence().available()
            );

            assertEquals(
                618L,
                result.reviewCountEvidence().reviewCount()
            );
        }
    }

    private ParsedDeal parsedDeal(
        String productUrl
    ) {

        return new ParsedDeal(
            ASIN,
            productUrl,
            "Produto de teste",
            null,
            new BigDecimal(
                "1898.00"
            ),
            new BigDecimal(
                "3599.00"
            ),
            null,
            null,
            null,
            null,
            OffsetDateTime.parse(
                "2026-09-24T18:00:00-03:00"
            ),
            "AMAZON_DEALS"
        );
    }

    private String customerReviewBlock() {

        return """
            <div
                id="averageCustomerReviews_feature_div"
                data-csa-c-asin="B0GVT7QXF7">
              <div id="averageCustomerReviews" data-asin="B0GVT7QXF7">
                <span id="acrPopover" title="4,8 de 5 estrelas"></span>
                <span
                    id="acrCustomerReviewText"
                    aria-label="618 Análises">
                </span>
              </div>
            </div>
            """;
    }

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

    private static final class TestHttpServer
        implements AutoCloseable {

        private final HttpServer server;

        private final ExecutorService executor;

        private TestHttpServer(
            HttpServer server,
            ExecutorService executor
        ) {

            this.server =
                server;

            this.executor =
                executor;
        }

        private static TestHttpServer start(
            String body
        ) throws Exception {

            HttpServer server =
                HttpServer.create(
                    new InetSocketAddress(
                        "127.0.0.1",
                        0
                    ),
                    0
                );

            server.createContext(
                "/product",
                exchange -> {

                    byte[] response =
                        body.getBytes(
                            StandardCharsets.UTF_8
                        );

                    exchange.sendResponseHeaders(
                        200,
                        response.length
                    );

                    try (var output =
                             exchange.getResponseBody()) {

                        output.write(
                            response
                        );
                    }
                }
            );

            ExecutorService executor =
                Executors.newCachedThreadPool();

            server.setExecutor(
                executor
            );

            server.start();

            return new TestHttpServer(
                server,
                executor
            );
        }

        private String url() {

            return "http://127.0.0.1:"
                + server.getAddress()
                    .getPort()
                + "/product";
        }

        @Override
        public void close() {

            server.stop(
                0
            );

            executor.shutdownNow();
        }
    }
}
