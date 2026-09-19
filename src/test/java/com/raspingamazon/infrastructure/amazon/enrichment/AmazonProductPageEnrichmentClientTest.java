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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes do client de enrichment da página individual do produto.
 *
 * <p>O servidor HTTP utilizado é local, portanto estes testes continuam
 * herméticos e não acessam a Amazon real.</p>
 *
 * <p>A resposta de sucesso utiliza uma fixture mínima contendo somente
 * as estruturas realmente consumidas pelo AmazonProductPageParser.</p>
 */
class AmazonProductPageEnrichmentClientTest {

    @Test
    void shouldEnrichParsedDealFromAmazonProductPage()
        throws Exception {

        String html =
            loadFixture(
                "amazon-amazon.html"
            );

        try (TestHttpServer server =
                 TestHttpServer.start(
                     200,
                     html
                 )) {

            AmazonProductPageEnrichmentClient client =
                new AmazonProductPageEnrichmentClient(
                    HttpClient.newHttpClient(),
                    new AmazonProductPageParser()
                );

            ParsedDeal parsedDeal =
                createParsedDeal(
                    server.url()
                );

            ProductEnrichmentResult result =
                client.enrich(
                    parsedDeal
                );

            assertNotNull(
                result
            );
        }
    }

    @Test
    void shouldRejectHttpError()
        throws Exception {

        try (TestHttpServer server =
                 TestHttpServer.start(
                     503,
                     "Service unavailable"
                 )) {

            AmazonProductPageEnrichmentClient client =
                new AmazonProductPageEnrichmentClient(
                    HttpClient.newHttpClient(),
                    new AmazonProductPageParser()
                );

            assertThrows(
                AmazonProductPageEnrichmentClient
                    .ProductEnrichmentException.class,
                () -> client.enrich(
                    createParsedDeal(
                        server.url()
                    )
                )
            );
        }
    }

    @Test
    void shouldRejectEmptyResponse()
        throws Exception {

        try (TestHttpServer server =
                 TestHttpServer.start(
                     200,
                     "   "
                 )) {

            AmazonProductPageEnrichmentClient client =
                new AmazonProductPageEnrichmentClient(
                    HttpClient.newHttpClient(),
                    new AmazonProductPageParser()
                );

            assertThrows(
                AmazonProductPageEnrichmentClient
                    .ProductEnrichmentException.class,
                () -> client.enrich(
                    createParsedDeal(
                        server.url()
                    )
                )
            );
        }
    }

    @Test
    void shouldRejectMissingProductUrl() {

        ParsedDeal parsedDeal =
            createParsedDeal(
                null
            );

        AmazonProductPageEnrichmentClient client =
            new AmazonProductPageEnrichmentClient(
                HttpClient.newHttpClient(),
                new AmazonProductPageParser()
            );

        assertThrows(
            AmazonProductPageEnrichmentClient
                .ProductEnrichmentException.class,
            () -> client.enrich(
                parsedDeal
            )
        );
    }

    @Test
    void shouldWrapTransportFailure() {

        AmazonProductPageEnrichmentClient client =
            new AmazonProductPageEnrichmentClient(
                HttpClient.newHttpClient(),
                new AmazonProductPageParser()
            );

        ParsedDeal parsedDeal =
            createParsedDeal(
                "http://127.0.0.1:1/product"
            );

        assertThrows(
            AmazonProductPageEnrichmentClient
                .ProductEnrichmentException.class,
            () -> client.enrich(
                parsedDeal
            )
        );
    }

    /**
     * Cria um ParsedDeal mínimo suficiente para os testes de enrichment.
     *
     * <p>rating e reviewCount são deliberadamente null porque este arquivo
     * testa somente a etapa posterior de seller/delivery.</p>
     */
    private ParsedDeal createParsedDeal(
        String productUrl
    ) {

        return new ParsedDeal(
            "B000000001",
            productUrl,
            "Produto de teste",
            "https://example.com/image.jpg",

            new BigDecimal(
                "100.00"
            ),

            new BigDecimal(
                "120.00"
            ),

            /*
             * previousPrice
             */
            null,

            /*
             * soldPercentage
             */
            null,

            /*
             * rating
             */
            null,

            /*
             * reviewCount
             */
            null,

            OffsetDateTime.now(),

            "AMAZON_DEALS"
        );
    }

    /**
     * Carrega fixture mínima de página de produto.
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

    /**
     * Servidor HTTP local usado para tornar o teste hermético.
     */
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

        static TestHttpServer start(
            int statusCode,
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
                        statusCode,
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

        String url() {

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
