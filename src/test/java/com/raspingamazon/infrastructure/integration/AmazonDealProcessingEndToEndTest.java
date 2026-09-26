package com.raspingamazon.infrastructure.integration;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.deal.AmazonDealProcessingService;
import com.raspingamazon.infrastructure.composition.AmazonDealProcessingComposition;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste vertical do pipeline de processamento.
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
 * elegibilidade estrutural
 *     ↓
 * COMMERCIAL_FILTER_V2
 *     ↓
 * SCORE_V2
 *     ↓
 * DealEvaluation
 *     ↓
 * PostgreSQL
 * </pre>
 *
 * <p>A fixture individual utilizada por este teste contém seller e
 * delivery Amazon, mas deliberadamente não contém condição CASH.</p>
 *
 * <p>Isso não torna mais o desconto indisponível. Pela ADR-0005,
 * quando não existe preço CASH explicitamente diferenciado,
 * currentPrice é utilizado como effectivePrice para comparação com
 * basisPrice.</p>
 *
 * <p>Nesta fixture:</p>
 *
 * <pre>
 * basisPrice     = 99.90
 * effectivePrice = 79.90
 * source         = CURRENT_PRICE
 * desconto       = 20.0200%
 * </pre>
 *
 * <p>Como o limiar do COMMERCIAL_FILTER_V2 é 20%, a oferta passa.</p>
 *
 * <p>O mesmo evento é processado duas vezes com o mesmo Clock fixo.
 * O teste comprova que a decisão permanece reproduzível sem duplicar
 * o estado persistido correspondente à mesma observação.</p>
 */
@PostgresIntegrationTest
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
                "AMAZON_SELLER_DELIVERY_V1",
                firstDecision.eligibilityPolicyVersion()
            );

            assertEquals(
                "COMMERCIAL_FILTER_V2",
                firstDecision.filterProfileVersion()
            );

            assertEquals(
                "SCORE_V2",
                firstDecision.scoreVersion()
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

            /*
             * O enrichment persiste quatro evidências auditáveis:
             * SELLER, DELIVERY, RATING e REVIEW_COUNT.
             */
            assertEvidenceTypes(
                connection,
                snapshotId
            );

            assertEquals(
                1L,
                countEvaluations(
                    connection,
                    snapshotId
                )
            );

            /*
             * 1. SELLER_IS_AMAZON
             * 2. DELIVERY_IS_AMAZON
             * 3. MIN_BASIS_DISCOUNT
             * 4. MIN_RATING
             * 5. MIN_REVIEW_COUNT
             */
            assertEquals(
                5L,
                countEvaluationRuleResults(
                    connection,
                    snapshotId
                )
            );

            /*
             * A oferta agora é elegível, logo SCORE_V2 persiste
             * quatro fatores auditáveis.
             */
            assertEquals(
                4L,
                countScoreFactors(
                    connection,
                    snapshotId
                )
            );

            assertCommercialRuleAudit(
                connection,
                snapshotId
            );

            assertSnapshotAuditData(
                connection,
                snapshotId
            );

            /*
             * ---------------------------------------------------------
             * SEGUNDO PROCESSAMENTO DA MESMA OBSERVAÇÃO
             * ---------------------------------------------------------
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

            assertEquals(
                snapshotId,
                snapshotIdAfterReplay
            );

            assertEquals(
                firstDecision,
                secondDecision
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

            /*
             * O replay idempotente não pode duplicar evidências.
             * Os mesmos quatro tipos devem continuar presentes
             * exatamente uma vez cada.
             */
            assertEvidenceTypes(
                connection,
                snapshotId
            );

            assertEquals(
                1L,
                countEvaluations(
                    connection,
                    snapshotId
                )
            );

            assertEquals(
                5L,
                countEvaluationRuleResults(
                    connection,
                    snapshotId
                )
            );

            assertEquals(
                4L,
                countScoreFactors(
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
     * Confirma a auditoria da nova regra comercial.
     */
    private void assertCommercialRuleAudit(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql = """
            SELECT
                derr.rule_code,
                derr.passed,
                derr.observed_value,
                derr.threshold_value,
                derr.reason_code
            FROM deal_evaluation_rule_result derr
            JOIN deal_evaluation de
              ON de.id = derr.deal_evaluation_id
            WHERE de.offer_snapshot_id = ?
              AND derr.rule_code = 'MIN_BASIS_DISCOUNT'
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

                assertEquals(
                    "MIN_BASIS_DISCOUNT",
                    resultSet.getString(
                        "rule_code"
                    )
                );

                assertTrue(
                    resultSet.getBoolean(
                        "passed"
                    )
                );

                assertEquals(
                    "DISCOUNT=20.0200|BASIS=99.90|EFFECTIVE=79.90|SOURCE=CURRENT_PRICE",
                    resultSet.getString(
                        "observed_value"
                    )
                );

                assertBigDecimalTextEquals(
                    "20",
                    resultSet.getString(
                        "threshold_value"
                    )
                );

                assertNull(
                    resultSet.getString(
                        "reason_code"
                    )
                );

                assertFalse(
                    resultSet.next()
                );
            }
        }
    }

    private void assertSnapshotAuditData(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql = """
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

    private void assertBigDecimalTextEquals(
        String expected,
        String actual
    ) {

        assertNotNull(
            actual
        );

        BigDecimal expectedValue =
            new BigDecimal(
                expected
            );

        BigDecimal actualValue =
            new BigDecimal(
                actual
            );

        assertEquals(
            0,
            expectedValue.compareTo(
                actualValue
            )
        );
    }

    private long findProductId(
        Connection connection,
        String asin
    ) throws Exception {

        String sql = """
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

    private long findSingleSnapshotId(
        Connection connection,
        long productId
    ) throws Exception {

        String sql = """
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

                assertFalse(
                    resultSet.next()
                );

                return snapshotId;
            }
        }
    }

    private DecisionState loadDecision(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql = """
                SELECT
                    eligible,
                    rejection_reason,
                    eligibility_policy_version,
                    filter_profile_version,
                    score_version
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
                        ),
                        resultSet.getString(
                            "eligibility_policy_version"
                        ),
                        resultSet.getString(
                            "filter_profile_version"
                        ),
                        resultSet.getString(
                            "score_version"
                        )
                    );

                assertFalse(
                    resultSet.next()
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

    /**
     * Confirma a provenance persistida pelo enrichment.
     *
     * <p>Não basta validar somente COUNT(*) = 4, porque quatro linhas
     * poderiam conter tipos duplicados e esconder a ausência de uma
     * evidência esperada.</p>
     *
     * <p>O contrato atual exige exatamente uma evidência de cada tipo:</p>
     *
     * <ul>
     *     <li>SELLER;</li>
     *     <li>DELIVERY;</li>
     *     <li>RATING;</li>
     *     <li>REVIEW_COUNT.</li>
     * </ul>
     *
     * <p>A combinação de tamanho da lista e igualdade do conjunto detecta
     * tanto duplicações quanto ausência de qualquer tipo esperado.</p>
     */
    private void assertEvidenceTypes(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql = """
            SELECT evidence_type
            FROM offer_evidence
            WHERE offer_snapshot_id = ?
            """;

        List<String> evidenceTypes =
            new ArrayList<>();

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

                while (resultSet.next()) {

                    evidenceTypes.add(
                        resultSet.getString(
                            "evidence_type"
                        )
                    );
                }
            }
        }

        assertEquals(
            4,
            evidenceTypes.size(),
            "Exactly four enrichment evidence rows must exist"
        );

        assertEquals(
            Set.of(
                "SELLER",
                "DELIVERY",
                "RATING",
                "REVIEW_COUNT"
            ),
            Set.copyOf(
                evidenceTypes
            ),
            "Enrichment evidence types must be complete and non-duplicated"
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

    private long countEvaluationRuleResults(
        Connection connection,
        long snapshotId
    ) throws Exception {

        return count(
            connection,
            """
            SELECT COUNT(*)
            FROM deal_evaluation_rule_result derr
            JOIN deal_evaluation de
              ON de.id = derr.deal_evaluation_id
            WHERE de.offer_snapshot_id = ?
            """,
            statement ->
                statement.setLong(
                    1,
                    snapshotId
                )
        );
    }

    private long countScoreFactors(
        Connection connection,
        long snapshotId
    ) throws Exception {

        return count(
            connection,
            """
            SELECT COUNT(*)
            FROM deal_evaluation_score_factor desf
            JOIN deal_evaluation de
              ON de.id = desf.deal_evaluation_id
            WHERE de.offer_snapshot_id = ?
            """,
            statement ->
                statement.setLong(
                    1,
                    snapshotId
                )
        );
    }

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
        String rejectionReason,
        String eligibilityPolicyVersion,
        String filterProfileVersion,
        String scoreVersion
    ) {
    }

    @FunctionalInterface
    private interface StatementBinder {

        void bind(
            PreparedStatement statement
        ) throws Exception;
    }

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
