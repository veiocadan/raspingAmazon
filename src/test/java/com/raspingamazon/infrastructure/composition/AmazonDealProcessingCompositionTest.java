package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.deal.AmazonDealProcessingService;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes do composition root do processamento Amazon.
 *
 * <p>Além da montagem estrutural, esta suíte prova que a composição HTTP
 * padrão conecta coleta e enrichment à persistência durável de
 * observabilidade sem utilizar a Amazon real.</p>
 */
@PostgresIntegrationTest
class AmazonDealProcessingCompositionTest {

    private static final String COLLECTION_INTEGRATION =
        "amazon-deals-http";

    private static final String PRODUCT_PAGE_INTEGRATION =
        "amazon-product-page";

    private static final String TEST_ASIN =
        "B000000001";

    private static final Instant COLLECTION_ONLY_INSTANT =
        Instant.parse(
            "2026-09-26T16:45:12Z"
        );

    private static final OffsetDateTime COLLECTION_ONLY_OBSERVED_AT =
        OffsetDateTime.ofInstant(
            COLLECTION_ONLY_INSTANT,
            ZoneOffset.UTC
        );

    private static final Clock COLLECTION_ONLY_CLOCK =
        Clock.fixed(
            COLLECTION_ONLY_INSTANT,
            ZoneOffset.UTC
        );

    private static final Instant FULL_PIPELINE_INSTANT =
        Instant.parse(
            "2026-09-26T17:15:00Z"
        );

    private static final OffsetDateTime FULL_PIPELINE_OBSERVED_AT =
        OffsetDateTime.ofInstant(
            FULL_PIPELINE_INSTANT,
            ZoneOffset.UTC
        );

    private static final Clock FULL_PIPELINE_CLOCK =
        Clock.fixed(
            FULL_PIPELINE_INSTANT,
            ZoneOffset.UTC
        );

    /**
     * Mantém a prova estrutural histórica: a composição padrão pode ser
     * construída sem executar uma chamada externa.
     */
    @Test
    void shouldBuildCompleteProcessingService()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            HttpClient httpClient =
                createHttpClient();

            AmazonDealProcessingService service =
                AmazonDealProcessingComposition.create(
                    connection,
                    Clock.systemUTC(),
                    httpClient
                );

            assertNotNull(
                service
            );
        }
    }

    /**
     * Prova o wiring real da coleta isoladamente.
     *
     * <p>O payload possui products=[] para que o parser produza zero
     * ofertas. Portanto nenhuma página individual é enriquecida.</p>
     */
    @Test
    void shouldPersistCollectionObservationThroughProductionComposition()
        throws Exception {

        HttpServer server =
            createCollectionOnlyServer();

        server.start();

        try {

            URI source =
                URI.create(
                    serverUrl(
                        server,
                        "/deals"
                    )
                );

            ApplicationConfig config =
                EnvironmentConfigProvider.load();

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                deleteCollectionOnlyTestObservations(
                    connection
                );

                try {

                    AmazonDealProcessingService service =
                        AmazonDealProcessingComposition.create(
                            connection,
                            COLLECTION_ONLY_CLOCK,
                            createHttpClient()
                        );

                    assertTrue(
                        service.process(
                            new CollectionRequest(
                                source
                            )
                        ).isEmpty()
                    );

                    assertPersistedCollectionObservation(
                        connection
                    );

                } finally {

                    deleteCollectionOnlyTestObservations(
                        connection
                    );
                }
            }

        } finally {

            server.stop(
                0
            );
        }
    }

    /**
     * Prova o grafo completo de observabilidade das duas integrações:
     *
     * <pre>
     * /deals
     *   -> JavaHttpTransport
     *   -> HttpCollectionCollector
     *   -> integration_observation
     *
     * /product
     *   -> HttpProductPageContentProvider
     *   -> AmazonProductPageEnrichmentClient
     *   -> integration_observation
     * </pre>
     *
     * <p>O teste abre uma transação externa e executa rollback no final.
     * Dessa forma Product, OfferSnapshot, Evidence, Evaluation e
     * integration_observation permanecem visíveis durante as asserções,
     * mas nenhum dado de teste é confirmado no banco.</p>
     */
    @Test
    void shouldPersistCollectionAndProductPageObservationsThroughProductionComposition()
        throws Exception {

        String productPageHtml =
            loadProductPageFixture(
                "amazon-amazon.html"
            );

        HttpServer server =
            createFullPipelineServer(
                productPageHtml
            );

        server.start();

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try {

            URI dealsUri =
                URI.create(
                    serverUrl(
                        server,
                        "/deals"
                    )
                );

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                connection.setAutoCommit(
                    false
                );

                try {

                    AmazonDealProcessingService service =
                        AmazonDealProcessingComposition.create(
                            connection,
                            FULL_PIPELINE_CLOCK,
                            createHttpClient()
                        );

                    var results =
                        service.process(
                            new CollectionRequest(
                                dealsUri
                            )
                        );

                    assertEquals(
                        1,
                        results.size()
                    );

                    assertFullPipelineObservations(
                        connection
                    );

                    /*
                     * Todo o pipeline está dentro da transação externa.
                     *
                     * A observabilidade usa savepoints e não executa
                     * commit pertencente ao chamador.
                     */
                    connection.rollback();

                    assertEquals(
                        0,
                        countFullPipelineObservations(
                            connection
                        )
                    );

                } finally {

                    /*
                     * Limpeza defensiva caso uma asserção anterior falhe.
                     */
                    connection.rollback();

                    connection.setAutoCommit(
                        true
                    );
                }
            }

        } finally {

            server.stop(
                0
            );
        }
    }

    private HttpClient createHttpClient() {

        return HttpClient.newBuilder()
            .connectTimeout(
                Duration.ofSeconds(
                    5
                )
            )
            .followRedirects(
                HttpClient.Redirect.NORMAL
            )
            .build();
    }

    /**
     * Servidor usado pelo teste que prova apenas a coleta.
     */
    private HttpServer createCollectionOnlyServer()
        throws IOException {

        HttpServer server =
            HttpServer.create(
                new InetSocketAddress(
                    "127.0.0.1",
                    0
                ),
                0
            );

        server.createContext(
            "/deals",
            exchange ->
                writeResponse(
                    exchange,
                    200,
                    """
                    {
                      "productSearchResponse": {
                        "products": []
                      }
                    }
                    """
                )
        );

        return server;
    }

    /**
     * Servidor local com as duas rotas necessárias ao pipeline completo.
     *
     * <p>A rota /deals devolve exatamente um produto. O link desse
     * produto aponta para /product no mesmo servidor, portanto não existe
     * qualquer acesso à internet pública.</p>
     */
    private HttpServer createFullPipelineServer(
        String productPageHtml
    ) throws IOException {

        HttpServer server =
            HttpServer.create(
                new InetSocketAddress(
                    "127.0.0.1",
                    0
                ),
                0
            );

        String productUrl =
            serverUrl(
                server,
                "/product"
            );

        String dealsBody =
            """
            {
              "productSearchResponse": {
                "products": [
                  {
                    "asin": "%s",
                    "title": "Produto observabilidade",
                    "link": "%s",
                    "price": {
                      "priceToPay": {
                        "price": "100.00"
                      },
                      "basisPrice": {
                        "price": "150.00"
                      }
                    },
                    "dealDetails": {
                      "percentClaimed": 50
                    },
                    "customerReviews": {
                      "rating": {
                        "shortDisplayString": "4,8"
                      },
                      "count": {
                        "value": 500
                      }
                    }
                  }
                ]
              }
            }
            """.formatted(
                TEST_ASIN,
                productUrl
            );

        server.createContext(
            "/deals",
            exchange ->
                writeResponse(
                    exchange,
                    200,
                    dealsBody
                )
        );

        server.createContext(
            "/product",
            exchange ->
                writeResponse(
                    exchange,
                    200,
                    productPageHtml
                )
        );

        return server;
    }

    private void writeResponse(
        HttpExchange exchange,
        int statusCode,
        String body
    ) throws IOException {

        byte[] responseBody =
            body.getBytes(
                StandardCharsets.UTF_8
            );

        exchange.getResponseHeaders()
            .set(
                "Content-Type",
                "text/html; charset=UTF-8"
            );

        exchange.sendResponseHeaders(
            statusCode,
            responseBody.length
        );

        try (OutputStream output =
                 exchange.getResponseBody()) {

            output.write(
                responseBody
            );

        } finally {

            exchange.close();
        }
    }

    private String serverUrl(
        HttpServer server,
        String path
    ) {

        return "http://127.0.0.1:"
            + server.getAddress()
            .getPort()
            + path;
    }

    private String loadProductPageFixture(
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
     * Valida a observação da coleta no teste de products=[].
     */
    private void assertPersistedCollectionObservation(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT
                observed_at,
                integration,
                operation,
                outcome,
                duration_ms,
                processing_run_id,
                processing_job_id,
                job_type,
                deal_candidate_id,
                offer_snapshot_id,
                deal_evaluation_id,
                publication_id,
                asin,
                failure_origin,
                failure_type,
                error_code,
                http_status_code
            FROM integration_observation
            WHERE integration = ?
              AND observed_at = ?
            ORDER BY id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                COLLECTION_INTEGRATION
            );

            statement.setObject(
                2,
                COLLECTION_ONLY_OBSERVED_AT
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                assertEquals(
                    COLLECTION_ONLY_OBSERVED_AT,
                    resultSet.getObject(
                        "observed_at",
                        OffsetDateTime.class
                    )
                );

                assertEquals(
                    COLLECTION_INTEGRATION,
                    resultSet.getString(
                        "integration"
                    )
                );

                assertEquals(
                    "GET",
                    resultSet.getString(
                        "operation"
                    )
                );

                assertEquals(
                    "SUCCESS",
                    resultSet.getString(
                        "outcome"
                    )
                );

                assertTrue(
                    resultSet.getLong(
                        "duration_ms"
                    ) >= 0L
                );

                assertNull(
                    resultSet.getObject(
                        "processing_run_id"
                    )
                );

                assertNull(
                    resultSet.getObject(
                        "processing_job_id"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "job_type"
                    )
                );

                assertNull(
                    resultSet.getObject(
                        "deal_candidate_id"
                    )
                );

                assertNull(
                    resultSet.getObject(
                        "offer_snapshot_id"
                    )
                );

                assertNull(
                    resultSet.getObject(
                        "deal_evaluation_id"
                    )
                );

                assertNull(
                    resultSet.getObject(
                        "publication_id"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "asin"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "failure_origin"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "failure_type"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "error_code"
                    )
                );

                assertEquals(
                    200,
                    resultSet.getObject(
                        "http_status_code",
                        Integer.class
                    )
                );

                assertFalse(
                    resultSet.next()
                );
            }
        }
    }

    /**
     * Valida as duas observações criadas pelo pipeline completo.
     */
    private void assertFullPipelineObservations(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT
                integration,
                operation,
                outcome,
                duration_ms,
                asin,
                failure_origin,
                failure_type,
                error_code,
                http_status_code
            FROM integration_observation
            WHERE observed_at = ?
              AND integration IN (?, ?)
            ORDER BY integration
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                FULL_PIPELINE_OBSERVED_AT
            );

            statement.setString(
                2,
                COLLECTION_INTEGRATION
            );

            statement.setString(
                3,
                PRODUCT_PAGE_INTEGRATION
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                /*
                 * ORDER BY integration:
                 *
                 * amazon-deals-http
                 * amazon-product-page
                 */
                assertTrue(
                    resultSet.next()
                );

                assertEquals(
                    COLLECTION_INTEGRATION,
                    resultSet.getString(
                        "integration"
                    )
                );

                assertEquals(
                    "GET",
                    resultSet.getString(
                        "operation"
                    )
                );

                assertEquals(
                    "SUCCESS",
                    resultSet.getString(
                        "outcome"
                    )
                );

                assertTrue(
                    resultSet.getLong(
                        "duration_ms"
                    ) >= 0L
                );

                assertNull(
                    resultSet.getString(
                        "asin"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "failure_origin"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "failure_type"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "error_code"
                    )
                );

                assertEquals(
                    200,
                    resultSet.getObject(
                        "http_status_code",
                        Integer.class
                    )
                );

                assertTrue(
                    resultSet.next()
                );

                assertEquals(
                    PRODUCT_PAGE_INTEGRATION,
                    resultSet.getString(
                        "integration"
                    )
                );

                assertEquals(
                    "LOAD",
                    resultSet.getString(
                        "operation"
                    )
                );

                assertEquals(
                    "SUCCESS",
                    resultSet.getString(
                        "outcome"
                    )
                );

                assertTrue(
                    resultSet.getLong(
                        "duration_ms"
                    ) >= 0L
                );

                /*
                 * O enrichment conhece o ASIN real sem consulta ou
                 * inferência adicional.
                 */
                assertEquals(
                    TEST_ASIN,
                    resultSet.getString(
                        "asin"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "failure_origin"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "failure_type"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "error_code"
                    )
                );

                /*
                 * O contrato ProductPageContentProvider não promete
                 * status HTTP porque também suporta estratégias não-HTTP.
                 */
                assertNull(
                    resultSet.getObject(
                        "http_status_code"
                    )
                );

                assertFalse(
                    resultSet.next()
                );
            }
        }
    }

    private int countFullPipelineObservations(
        Connection connection
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM integration_observation
            WHERE observed_at = ?
              AND integration IN (?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setObject(
                1,
                FULL_PIPELINE_OBSERVED_AT
            );

            statement.setString(
                2,
                COLLECTION_INTEGRATION
            );

            statement.setString(
                3,
                PRODUCT_PAGE_INTEGRATION
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getInt(
                    1
                );
            }
        }
    }

    private void deleteCollectionOnlyTestObservations(
        Connection connection
    ) throws Exception {

        String sql =
            """
            DELETE FROM integration_observation
            WHERE integration = ?
              AND observed_at = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                COLLECTION_INTEGRATION
            );

            statement.setObject(
                2,
                COLLECTION_ONLY_OBSERVED_AT
            );

            statement.executeUpdate();
        }
    }
}
