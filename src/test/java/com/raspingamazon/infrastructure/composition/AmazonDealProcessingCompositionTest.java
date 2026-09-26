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
 * padrão conecta a coleta à persistência durável de observabilidade sem
 * utilizar a Amazon real.</p>
 */
@PostgresIntegrationTest
class AmazonDealProcessingCompositionTest {

    private static final String COLLECTION_INTEGRATION =
        "amazon-deals-http";

    private static final Instant OBSERVATION_INSTANT =
        Instant.parse(
            "2026-09-26T16:45:12Z"
        );

    private static final OffsetDateTime OBSERVED_AT =
        OffsetDateTime.ofInstant(
            OBSERVATION_INSTANT,
            ZoneOffset.UTC
        );

    private static final Clock FIXED_CLOCK =
        Clock.fixed(
            OBSERVATION_INSTANT,
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
                HttpClient.newBuilder()
                    .connectTimeout(
                        Duration.ofSeconds(
                            5
                        )
                    )
                    .build();

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
     * Prova o wiring real:
     *
     * <pre>
     * servidor HTTP local
     *     -> JavaHttpTransport
     *     -> HttpCollectionCollector
     *     -> IntegrationObservationRecorder
     *     -> JdbcIntegrationObservationPersistenceAdapter
     *     -> PostgreSQL
     * </pre>
     *
     * <p>O payload possui products=[] para que o parser produza zero
     * ofertas. Com isso o teste termina depois da coleta e não acessa
     * a fronteira de enrichment.</p>
     */
    @Test
    void shouldPersistCollectionObservationThroughProductionComposition()
        throws Exception {

        HttpServer server =
            createCollectionServer();

        server.start();

        try {

            URI source =
                URI.create(
                    "http://127.0.0.1:"
                        + server.getAddress()
                        .getPort()
                        + "/deals"
                );

            ApplicationConfig config =
                EnvironmentConfigProvider.load();

            try (Connection connection =
                     DatabaseConnection.open(
                         config
                     )) {

                deleteTestObservations(
                    connection
                );

                try {

                    HttpClient httpClient =
                        HttpClient.newBuilder()
                            .connectTimeout(
                                Duration.ofSeconds(
                                    5
                                )
                            )
                            .build();

                    AmazonDealProcessingService service =
                        AmazonDealProcessingComposition.create(
                            connection,
                            FIXED_CLOCK,
                            httpClient
                        );

                    /*
                     * products=[] garante que nenhuma página individual
                     * de produto seja enriquecida.
                     */
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

                    deleteTestObservations(
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
     * Servidor HTTP estritamente local para a prova de composição.
     *
     * <p>Não existe acesso à Amazon, internet pública ou mock do
     * composition root. O HttpClient real executa uma chamada HTTP
     * contra uma porta efêmera em 127.0.0.1.</p>
     */
    private HttpServer createCollectionServer()
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
            this::handleCollectionRequest
        );

        return server;
    }

    private void handleCollectionRequest(
        HttpExchange exchange
    ) throws IOException {

        byte[] responseBody =
            """
            {
              "productSearchResponse": {
                "products": []
              }
            }
            """
                .getBytes(
                    StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders()
            .set(
                "Content-Type",
                "application/json; charset=UTF-8"
            );

        exchange.sendResponseHeaders(
            200,
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

    /**
     * Confere o fato persistido em vez de inspecionar campos privados
     * da composição.
     *
     * <p>Esse formato é intencional: o teste observa o comportamento
     * externo da composição real e não depende de reflection.</p>
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
                OBSERVED_AT
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                assertEquals(
                    OBSERVED_AT,
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

                /*
                 * A latência varia conforme a máquina.
                 * A propriedade relevante aqui é ser uma duração
                 * monotônica não negativa.
                 */
                assertTrue(
                    resultSet.getLong(
                        "duration_ms"
                    ) >= 0L
                );

                /*
                 * O collector síncrono recebe somente CollectionRequest.
                 * Nenhuma identidade de pipeline é inventada.
                 */
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

                /*
                 * SUCCESS não possui metadados de falha.
                 */
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

                /*
                 * Uma chamada HTTP produz uma única observação.
                 */
                assertFalse(
                    resultSet.next()
                );
            }
        }
    }

    /**
     * O timestamp fixo torna a linha de teste determinística e permite
     * limpeza específica sem tocar em observações operacionais reais.
     */
    private void deleteTestObservations(
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
                OBSERVED_AT
            );

            statement.executeUpdate();
        }
    }
}
