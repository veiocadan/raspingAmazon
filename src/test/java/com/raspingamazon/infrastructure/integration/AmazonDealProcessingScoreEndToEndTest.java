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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste vertical específico do score ativo.
 *
 * <p>Após a V16, o caminho positivo completo utiliza:</p>
 *
 * <pre>
 * COMMERCIAL_FILTER_V2
 *     ↓
 * MIN_BASIS_DISCOUNT
 *     ↓
 * SCORE_V2
 *     ↓
 * BASIS_DISCOUNT
 * </pre>
 *
 * <p>A fixture contém:</p>
 *
 * <pre>
 * currentPrice = 79.90
 * basisPrice   = 99.90
 * CASH price   = 79.90
 *
 * basisDiscount = 20.0200%
 * </pre>
 */
class AmazonDealProcessingScoreEndToEndTest {

    private static final String ASIN =
        "B0E2E85001";

    private static final String DEALS_FIXTURE =
        "amazon/fixtures/deals/end-to-end-deal.html";

    private static final String PRODUCT_FIXTURE =
        "amazon/fixtures/product/amazon-commercial.html";

    private static final Instant COLLECTION_INSTANT =
        Instant.parse(
            "2026-09-19T03:00:00Z"
        );

    @Test
    void shouldPersistScoreAndAuditableFactorsForApprovedOffer()
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

            var result =
                service.process(
                    request
                );

            assertEquals(
                1,
                result.size()
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

            PersistedScoreEvaluation evaluation =
                loadScoreEvaluation(
                    connection,
                    snapshotId
                );

            assertTrue(
                evaluation.eligible()
            );

            assertNull(
                evaluation.rejectionReason()
            );

            assertEquals(
                "AMAZON_SELLER_DELIVERY_V1",
                evaluation.eligibilityPolicyVersion()
            );

            assertEquals(
                "COMMERCIAL_FILTER_V2",
                evaluation.filterProfileVersion()
            );

            assertEquals(
                "SCORE_V2",
                evaluation.scoreVersion()
            );

            /*
             * SCORE_V2:
             *
             * soldPercentage:
             * 37 / 100 * 30 = 11.1000
             *
             * basisDiscount:
             * ((99.90 - 79.90) / 99.90) * 100 = 20.0200
             * 20.0200 / 100 * 25 = 5.0050
             *
             * rating:
             * 4.6 / 5 * 100 = 92
             * 92 / 100 * 20 = 18.4000
             *
             * reviewCount:
             * 58363 >= 1000
             * contribuição = 15.0000
             *
             * total = 49.5050
             */
            assertBigDecimalEquals(
                "49.5050",
                evaluation.score()
            );

            List<PersistedScoreFactor> factors =
                loadScoreFactors(
                    connection,
                    evaluation.evaluationId()
                );

            assertEquals(
                4,
                factors.size()
            );

            assertSoldPercentageFactor(
                factors.get(0)
            );

            assertBasisDiscountFactor(
                factors.get(1)
            );

            assertRatingFactor(
                factors.get(2)
            );

            assertReviewCountFactor(
                factors.get(3)
            );

            assertEquals(
                1L,
                countEvaluations(
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

    private void assertSoldPercentageFactor(
        PersistedScoreFactor factor
    ) {

        assertEquals(
            0,
            factor.factorOrder()
        );

        assertEquals(
            "SOLD_PERCENTAGE",
            factor.factorCode()
        );

        assertEquals(
            "AVAILABLE",
            factor.status()
        );

        assertBigDecimalEquals(
            "37",
            factor.rawValue()
        );

        assertBigDecimalEquals(
            "37.0000",
            factor.normalizedValue()
        );

        assertBigDecimalEquals(
            "30.0000",
            factor.weight()
        );

        assertBigDecimalEquals(
            "11.1000",
            factor.contribution()
        );
    }

    private void assertBasisDiscountFactor(
        PersistedScoreFactor factor
    ) {

        assertEquals(
            1,
            factor.factorOrder()
        );

        assertEquals(
            "BASIS_DISCOUNT",
            factor.factorCode()
        );

        assertEquals(
            "AVAILABLE",
            factor.status()
        );

        assertBigDecimalEquals(
            "20.0200",
            factor.rawValue()
        );

        assertBigDecimalEquals(
            "20.0200",
            factor.normalizedValue()
        );

        assertBigDecimalEquals(
            "25.0000",
            factor.weight()
        );

        assertBigDecimalEquals(
            "5.0050",
            factor.contribution()
        );
    }

    private void assertRatingFactor(
        PersistedScoreFactor factor
    ) {

        assertEquals(
            2,
            factor.factorOrder()
        );

        assertEquals(
            "RATING",
            factor.factorCode()
        );

        assertEquals(
            "AVAILABLE",
            factor.status()
        );

        assertBigDecimalEquals(
            "4.6",
            factor.rawValue()
        );

        assertBigDecimalEquals(
            "92.0000",
            factor.normalizedValue()
        );

        assertBigDecimalEquals(
            "20.0000",
            factor.weight()
        );

        assertBigDecimalEquals(
            "18.4000",
            factor.contribution()
        );
    }

    private void assertReviewCountFactor(
        PersistedScoreFactor factor
    ) {

        assertEquals(
            3,
            factor.factorOrder()
        );

        assertEquals(
            "REVIEW_COUNT",
            factor.factorCode()
        );

        assertEquals(
            "AVAILABLE",
            factor.status()
        );

        assertBigDecimalEquals(
            "58363",
            factor.rawValue()
        );

        assertBigDecimalEquals(
            "100.0000",
            factor.normalizedValue()
        );

        assertBigDecimalEquals(
            "15.0000",
            factor.weight()
        );

        assertBigDecimalEquals(
            "15.0000",
            factor.contribution()
        );
    }

    private PersistedScoreEvaluation loadScoreEvaluation(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql = """
                SELECT
                    id,
                    eligible,
                    rejection_reason,
                    eligibility_policy_version,
                    filter_profile_version,
                    score,
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

                PersistedScoreEvaluation evaluation =
                    new PersistedScoreEvaluation(
                        resultSet.getLong(
                            "id"
                        ),
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
                        resultSet.getBigDecimal(
                            "score"
                        ),
                        resultSet.getString(
                            "score_version"
                        )
                    );

                assertFalse(
                    resultSet.next()
                );

                return evaluation;
            }
        }
    }

    private List<PersistedScoreFactor> loadScoreFactors(
        Connection connection,
        long evaluationId
    ) throws Exception {

        String sql = """
                SELECT
                    factor_order,
                    factor_code,
                    status,
                    raw_value,
                    normalized_value,
                    weight,
                    contribution
                FROM deal_evaluation_score_factor
                WHERE deal_evaluation_id = ?
                ORDER BY factor_order
                """;

        List<PersistedScoreFactor> factors =
            new ArrayList<>();

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                while (resultSet.next()) {

                    factors.add(
                        new PersistedScoreFactor(
                            resultSet.getInt(
                                "factor_order"
                            ),
                            resultSet.getString(
                                "factor_code"
                            ),
                            resultSet.getString(
                                "status"
                            ),
                            resultSet.getBigDecimal(
                                "raw_value"
                            ),
                            resultSet.getBigDecimal(
                                "normalized_value"
                            ),
                            resultSet.getBigDecimal(
                                "weight"
                            ),
                            resultSet.getBigDecimal(
                                "contribution"
                            )
                        )
                    );
                }
            }
        }

        return factors;
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
            snapshotId
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
            snapshotId
        );
    }

    private long count(
        Connection connection,
        String sql,
        long value
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                value
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

    private record PersistedScoreEvaluation(
        long evaluationId,
        boolean eligible,
        String rejectionReason,
        String eligibilityPolicyVersion,
        String filterProfileVersion,
        BigDecimal score,
        String scoreVersion
    ) {
    }

    private record PersistedScoreFactor(
        int factorOrder,
        String factorCode,
        String status,
        BigDecimal rawValue,
        BigDecimal normalizedValue,
        BigDecimal weight,
        BigDecimal contribution
    ) {
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
