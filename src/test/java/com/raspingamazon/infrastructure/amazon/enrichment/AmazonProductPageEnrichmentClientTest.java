package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes do cliente de enriquecimento da página individual.
 *
 * <p>O teste principal verifica o contrato inteiro campo a campo.
 * Isso evita regressões silenciosas causadas por argumentos String
 * posicionais, que foi justamente o problema identificado na auditoria.</p>
 */
class AmazonProductPageEnrichmentClientTest {

    @Test
    void shouldEnrichParsedDealAndPreserveAllEvidenceFields()
            throws Exception {

        String html =
                loadFixture(
                        "totalamazon.html"
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

            /*
             * ASIN continua sendo o ASIN recebido do ParsedDeal.
             */
            assertEquals(
                    "B000000001",
                    result.asin()
            );

            /*
             * Seller:
             * verificamos valor bruto, classificação e provenance.
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
             * Delivery:
             * verificamos novamente valor bruto e classificação.
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
             * Nesta fixture, a origem real da evidência de entrega
             * é merchantInfoFeature.
             *
             * O parser chegou a ela através do fallback da estrutura
             * combinada "Enviado / Vendido".
             *
             * Preservamos esse fato em vez de inventar uma origem
             * fulfillerInfoFeature que não foi utilizada.
             */
            assertEquals(
                    "merchantInfoFeature",
                    result.deliveryEvidence().source()
            );

            /*
             * A fonte geral do enriquecimento é o adaptador utilizado.
             */
            assertEquals(
                    "AMAZON_PRODUCT_PAGE",
                    result.source()
            );

            /*
             * A URL possui campo próprio e não pode mais ocupar
             * acidentalmente o campo source.
             */
            assertEquals(
                    server.url(),
                    result.productUrl()
            );

            /*
             * Clock ainda não foi injetado nesta subfase.
             * Por enquanto verificamos apenas sua presença.
             */
            assertNotNull(
                    result.enrichedAt()
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
     * Cria um ParsedDeal de teste.
     *
     * <p>O título é propositalmente diferente do seller para que uma
     * regressão de mapeamento volte a ser detectada pelos assertions.</p>
     */
    private ParsedDeal createParsedDeal(
            String productUrl
    ) {

        return new ParsedDeal(
                "B000000001",
                productUrl,
                "Produto de teste",
                "https://example.com/image.jpg",
                new BigDecimal("100.00"),
                new BigDecimal("120.00"),
                null,
                null,
                OffsetDateTime.now(),
                "AMAZON_DEALS"
        );
    }

    /**
     * Carrega uma fixture existente.
     */
    private String loadFixture(
            String fileName
    ) throws Exception {

        try (var inputStream =
                     getClass()
                             .getResourceAsStream(
                                     "/amazon/"
                                             + fileName
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

    /**
     * Servidor HTTP local utilizado pelos testes.
     *
     * <p>Isso permite testar o cliente HTTP sem depender da Amazon real.</p>
     */
    private static final class TestHttpServer
            implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor;

        private TestHttpServer(
                HttpServer server,
                ExecutorService executor
        ) {
            this.server = server;
            this.executor = executor;
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
                    + server.getAddress().getPort()
                    + "/product";
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}