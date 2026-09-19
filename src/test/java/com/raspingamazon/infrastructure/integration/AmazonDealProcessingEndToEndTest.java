package com.raspingamazon.infrastructure.integration;

import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.deal.AmazonDealProcessingService;
import com.raspingamazon.infrastructure.composition.AmazonDealProcessingComposition;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
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
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste vertical de saída da FASE 8.5.
 *
 * <p>Exercita, sem acesso externo à Amazon:</p>
 *
 * <pre>
 * fixture Deals
 *     ↓
 * coleta HTTP local
 *     ↓
 * parser
 *     ↓
 * fixture de produto
 *     ↓
 * enrichment
 *     ↓
 * Product
 *     ↓
 * OfferSnapshot
 *     ↓
 * Evidence
 *     ↓
 * DealEvaluation
 *     ↓
 * PostgreSQL
 * </pre>
 *
 * <p>O mesmo evento é processado duas vezes com o mesmo Clock fixo.
 * O teste comprova que a decisão permanece reproduzível sem duplicar
 * o estado persistido correspondente à mesma observação.</p>
 */
class AmazonDealProcessingEndToEndTest {

    private static final String ASIN =
            "B0E2E85001";

    private static final String DEALS_FIXTURE =
            "amazon/fixtures/deals/end-to-end-deal.html";

    private static final String PRODUCT_FIXTURE =
            "amazon/fixtures/product/amazon-amazon.html";

    private static final Instant COLLECTION_INSTANT =
            Instant.parse(
                    "2026-09-19T03:00:00Z"
            );

    @Test
    void shouldProcessAuditAndReprocessSameObservationWithoutDuplication()
            throws Exception {

        ApplicationConfig config =
                EnvironmentConfigProvider.load();

        DatabaseMigration.migrate(
                config
        );

        String dealsHtml =
                loadFixture(
                        DEALS_FIXTURE
                );

        String productHtml =
                loadFixture(
                        PRODUCT_FIXTURE
                );

        Clock fixedClock =
                Clock.fixed(
                        COLLECTION_INSTANT,
                        ZoneOffset.UTC
                );

        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(
                                        5
                                )
                        )
                        .build();

        try (LocalAmazonServer server =
                     LocalAmazonServer.start(
                             dealsHtml,
                             productHtml
                     );

             Connection connection =
                     DatabaseConnection.open(
                             config
                     )) {

            cleanupByAsin(
                    connection,
                    ASIN
            );

            AmazonDealProcessingService service =
                    AmazonDealProcessingComposition.create(
                            connection,
                            fixedClock,
                            httpClient
                    );

            CollectionRequest request =
                    new CollectionRequest(
                            URI.create(
                                    server.dealsUrl()
                            )
                    );

            /*
             * ---------------------------------------------------------
             * PRIMEIRO PROCESSAMENTO
             * ---------------------------------------------------------
             */
            var firstResult =
                    service.process(
                            request
                    );

            assertEquals(
                    1,
                    firstResult.size()
            );

            long productId =
                    findProductId(
                            connection,
                            ASIN
                    );

            long snapshotId =
                    findSingleSnapshotId(
                            connection,
                            productId
                    );

            DecisionState firstDecision =
                    loadDecision(
                            connection,
                            snapshotId
                    );

            assertTrue(
                    firstDecision.eligible()
            );

            assertNull(
                    firstDecision.rejectionReason()
            );

            assertEquals(
                    1L,
                    countProducts(
                            connection,
                            ASIN
                    )
            );

            assertEquals(
                    1L,
                    countSnapshots(
                            connection,
                            productId
                    )
            );

            assertEquals(
                    2L,
                    countEvidence(
                            connection,
                            snapshotId
                    )
            );

            assertEquals(
                    1L,
                    countEvaluations(
                            connection,
                            snapshotId
                    )
            );

            assertSnapshotAuditData(
                    connection,
                    snapshotId
            );

            /*
             * ---------------------------------------------------------
             * SEGUNDO PROCESSAMENTO DA MESMA OBSERVAÇÃO
             * ---------------------------------------------------------
             *
             * O Clock fixo mantém collected_at igual.
             * A URL/source também é a mesma.
             *
             * Portanto, a identidade de OfferSnapshot deve ser a mesma:
             *
             * product_id + collected_at + source
             */
            var secondResult =
                    service.process(
                            request
                    );

            assertEquals(
                    1,
                    secondResult.size()
            );

            long snapshotIdAfterReplay =
                    findSingleSnapshotId(
                            connection,
                            productId
                    );

            DecisionState secondDecision =
                    loadDecision(
                            connection,
                            snapshotIdAfterReplay
                    );

            /*
             * A mesma observação deve apontar para o mesmo snapshot.
             */
            assertEquals(
                    snapshotId,
                    snapshotIdAfterReplay
            );

            /*
             * A decisão persistida precisa permanecer reproduzível.
             */
            assertEquals(
                    firstDecision,
                    secondDecision
            );

            /*
             * Nenhum estado dependente pode ser duplicado.
             */
            assertEquals(
                    1L,
                    countProducts(
                            connection,
                            ASIN
                    )
            );

            assertEquals(
                    1L,
                    countSnapshots(
                            connection,
                            productId
                    )
            );

            assertEquals(
                    2L,
                    countEvidence(
                            connection,
                            snapshotId
                    )
            );

            assertEquals(
                    1L,
                    countEvaluations(
                            connection,
                            snapshotId
                    )
            );

        } finally {

            try (Connection cleanupConnection =
                         DatabaseConnection.open(
                                 config
                         )) {

                cleanupByAsin(
                        cleanupConnection,
                        ASIN
                );
            }
        }
    }

    /**
     * Confirma que os dados materiais da observação foram persistidos.
     *
     * <p>Valores NUMERIC do PostgreSQL são comparados numericamente,
     * sem exigir uma escala textual específica. Portanto, por exemplo,
     * 37 e 37.00 representam corretamente o mesmo valor.</p>
     */
    private void assertSnapshotAuditData(
            Connection connection,
            long snapshotId
    ) throws Exception {

        String sql =
                """
                SELECT
                    current_price,
                    basis_price,
                    sold_percentage,
                    rating,
                    review_count,
                    seller_name,
                    delivery_provider
                FROM offer_snapshot
                WHERE id = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     )) {

            statement.setLong(
                    1,
                    snapshotId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next()
                );

                assertBigDecimalEquals(
                        "79.90",
                        resultSet.getBigDecimal(
                                "current_price"
                        )
                );

                assertBigDecimalEquals(
                        "99.90",
                        resultSet.getBigDecimal(
                                "basis_price"
                        )
                );

                assertBigDecimalEquals(
                        "37",
                        resultSet.getBigDecimal(
                                "sold_percentage"
                        )
                );

                assertEquals(
                        4.6,
                        resultSet.getDouble(
                                "rating"
                        )
                );

                assertEquals(
                        58363L,
                        resultSet.getLong(
                                "review_count"
                        )
                );

                assertEquals(
                        "Amazon.com.br",
                        resultSet.getString(
                                "seller_name"
                        )
                );

                assertEquals(
                        "Amazon",
                        resultSet.getString(
                                "delivery_provider"
                        )
                );
            }
        }
    }

    /**
     * Compara BigDecimal por valor numérico, não por escala.
     *
     * <p>BigDecimal.equals() considera a escala. Por isso,
     * 37 e 37.00 não são iguais segundo equals(), apesar de
     * representarem o mesmo número.</p>
     */
    private void assertBigDecimalEquals(
            String expected,
            BigDecimal actual
    ) {

        assertNotNull(
                actual
        );

        BigDecimal expectedValue =
                new BigDecimal(
                        expected
                );

        assertEquals(
                0,
                expectedValue.compareTo(
                        actual
                ),
                "Expected numeric value "
                        + expectedValue.toPlainString()
                        + " but was "
                        + actual.toPlainString()
        );
    }

    /**
     * Obtém o produto criado pelo fluxo.
     */
    private long findProductId(
            Connection connection,
            String asin
    ) throws Exception {

        String sql =
                """
                SELECT id
                FROM product
                WHERE asin = ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     )) {

            statement.setString(
                    1,
                    asin
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next()
                );

                long id =
                        resultSet.getLong(
                                "id"
                        );

                assertTrue(
                        id > 0
                );

                return id;
            }
        }
    }

    /**
     * Obtém o único snapshot associado ao produto do teste.
     */
    private long findSingleSnapshotId(
            Connection connection,
            long productId
    ) throws Exception {

        String sql =
                """
                SELECT id
                FROM offer_snapshot
                WHERE product_id = ?
                ORDER BY id
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     )) {

            statement.setLong(
                    1,
                    productId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next()
                );

                long snapshotId =
                        resultSet.getLong(
                                "id"
                        );

                assertTrue(
                        snapshotId > 0
                );

                /*
                 * Não deve existir um segundo snapshot.
                 */
                assertTrue(
                        !resultSet.next()
                );

                return snapshotId;
            }
        }
    }

    /**
     * Carrega a decisão persistida para o snapshot.
     */
    private DecisionState loadDecision(
            Connection connection,
            long snapshotId
    ) throws Exception {

        String sql =
                """
                SELECT
                    eligible,
                    rejection_reason
                FROM deal_evaluation
                WHERE offer_snapshot_id = ?
                ORDER BY id
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     )) {

            statement.setLong(
                    1,
                    snapshotId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next()
                );

                DecisionState decision =
                        new DecisionState(
                                resultSet.getBoolean(
                                        "eligible"
                                ),
                                resultSet.getString(
                                        "rejection_reason"
                                )
                        );

                /*
                 * A mesma observação deve possuir somente uma avaliação.
                 */
                assertTrue(
                        !resultSet.next()
                );

                return decision;
            }
        }
    }

    private long countProducts(
            Connection connection,
            String asin
    ) throws Exception {

        return count(
                connection,
                """
                SELECT COUNT(*)
                FROM product
                WHERE asin = ?
                """,
                statement ->
                        statement.setString(
                                1,
                                asin
                        )
        );
    }

    private long countSnapshots(
            Connection connection,
            long productId
    ) throws Exception {

        return count(
                connection,
                """
                SELECT COUNT(*)
                FROM offer_snapshot
                WHERE product_id = ?
                """,
                statement ->
                        statement.setLong(
                                1,
                                productId
                        )
        );
    }

    private long countEvidence(
            Connection connection,
            long snapshotId
    ) throws Exception {

        return count(
                connection,
                """
                SELECT COUNT(*)
                FROM offer_evidence
                WHERE offer_snapshot_id = ?
                """,
                statement ->
                        statement.setLong(
                                1,
                                snapshotId
                        )
        );
    }

    private long countEvaluations(
            Connection connection,
            long snapshotId
    ) throws Exception {

        return count(
                connection,
                """
                SELECT COUNT(*)
                FROM deal_evaluation
                WHERE offer_snapshot_id = ?
                """,
                statement ->
                        statement.setLong(
                                1,
                                snapshotId
                        )
        );
    }

    /**
     * Helper de COUNT para evitar duplicação de JDBC no teste.
     */
    private long count(
            Connection connection,
            String sql,
            StatementBinder binder
    ) throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     )) {

            binder.bind(
                    statement
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next()
                );

                return resultSet.getLong(
                        1
                );
            }
        }
    }

    /**
     * Remove somente os registros pertencentes ao ASIN exclusivo
     * deste teste.
     *
     * <p>A remoção é feita de filhos para pais para respeitar
     * as foreign keys.</p>
     */
    private void cleanupByAsin(
            Connection connection,
            String asin
    ) throws Exception {

        executeDelete(
                connection,
                """
                DELETE FROM deal_evaluation_rule_result
                WHERE deal_evaluation_id IN (
                    SELECT de.id
                    FROM deal_evaluation de
                    JOIN offer_snapshot os
                      ON os.id = de.offer_snapshot_id
                    JOIN product p
                      ON p.id = os.product_id
                    WHERE p.asin = ?
                )
                """,
                asin
        );

        executeDelete(
                connection,
                """
                DELETE FROM publication_attempt
                WHERE publication_id IN (
                    SELECT pub.id
                    FROM publication pub
                    JOIN deal_evaluation de
                      ON de.id = pub.deal_evaluation_id
                    JOIN offer_snapshot os
                      ON os.id = de.offer_snapshot_id
                    JOIN product p
                      ON p.id = os.product_id
                    WHERE p.asin = ?
                )
                """,
                asin
        );

        executeDelete(
                connection,
                """
                DELETE FROM publication
                WHERE deal_evaluation_id IN (
                    SELECT de.id
                    FROM deal_evaluation de
                    JOIN offer_snapshot os
                      ON os.id = de.offer_snapshot_id
                    JOIN product p
                      ON p.id = os.product_id
                    WHERE p.asin = ?
                )
                """,
                asin
        );

        executeDelete(
                connection,
                """
                DELETE FROM deal_evaluation
                WHERE offer_snapshot_id IN (
                    SELECT os.id
                    FROM offer_snapshot os
                    JOIN product p
                      ON p.id = os.product_id
                    WHERE p.asin = ?
                )
                """,
                asin
        );

        executeDelete(
                connection,
                """
                DELETE FROM offer_evidence
                WHERE offer_snapshot_id IN (
                    SELECT os.id
                    FROM offer_snapshot os
                    JOIN product p
                      ON p.id = os.product_id
                    WHERE p.asin = ?
                )
                """,
                asin
        );

        executeDelete(
                connection,
                """
                DELETE FROM offer_payment_condition_method
                WHERE payment_condition_id IN (
                    SELECT opc.id
                    FROM offer_payment_condition opc
                    JOIN offer_snapshot os
                      ON os.id = opc.offer_snapshot_id
                    JOIN product p
                      ON p.id = os.product_id
                    WHERE p.asin = ?
                )
                """,
                asin
        );

        executeDelete(
                connection,
                """
                DELETE FROM offer_payment_condition
                WHERE offer_snapshot_id IN (
                    SELECT os.id
                    FROM offer_snapshot os
                    JOIN product p
                      ON p.id = os.product_id
                    WHERE p.asin = ?
                )
                """,
                asin
        );

        executeDelete(
                connection,
                """
                DELETE FROM offer_snapshot
                WHERE product_id IN (
                    SELECT id
                    FROM product
                    WHERE asin = ?
                )
                """,
                asin
        );

        executeDelete(
                connection,
                """
                DELETE FROM product
                WHERE asin = ?
                """,
                asin
        );
    }

    private void executeDelete(
            Connection connection,
            String sql,
            String asin
    ) throws Exception {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql
                     )) {

            statement.setString(
                    1,
                    asin
            );

            statement.executeUpdate();
        }
    }

    /**
     * Lê uma fixture textual do classpath.
     */
    private String loadFixture(
            String resourcePath
    ) throws Exception {

        ClassLoader classLoader =
                getClass()
                        .getClassLoader();

        try (InputStream inputStream =
                     classLoader.getResourceAsStream(
                             resourcePath
                     )) {

            assertNotNull(
                    inputStream,
                    "Fixture not found: "
                            + resourcePath
            );

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }

    private record DecisionState(
            boolean eligible,
            String rejectionReason
    ) {
    }

    @FunctionalInterface
    private interface StatementBinder {

        void bind(
                PreparedStatement statement
        ) throws Exception;
    }

    /**
     * Servidor local que simula as duas fronteiras HTTP do fluxo:
     *
     * - página de Deals;
     * - página individual do produto.
     */
    private static final class LocalAmazonServer
            implements AutoCloseable {

        private final HttpServer server;

        private final ExecutorService executor;

        private LocalAmazonServer(
                HttpServer server,
                ExecutorService executor
        ) {
            this.server =
                    server;

            this.executor =
                    executor;
        }

        static LocalAmazonServer start(
                String dealsHtml,
                String productHtml
        ) throws IOException {

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
                            respond(
                                    exchange,
                                    200,
                                    dealsHtml
                            )
            );

            server.createContext(
                    "/product/B0E2E85001",
                    exchange ->
                            respond(
                                    exchange,
                                    200,
                                    productHtml
                            )
            );

            ExecutorService executor =
                    Executors.newCachedThreadPool();

            server.setExecutor(
                    executor
            );

            server.start();

            return new LocalAmazonServer(
                    server,
                    executor
            );
        }

        String dealsUrl() {

            return "http://127.0.0.1:"
                    + server
                    .getAddress()
                    .getPort()
                    + "/deals";
        }

        private static void respond(
                HttpExchange exchange,
                int statusCode,
                String body
        ) throws IOException {

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

        @Override
        public void close() {

            server.stop(
                    0
            );

            executor.shutdownNow();
        }
    }
}