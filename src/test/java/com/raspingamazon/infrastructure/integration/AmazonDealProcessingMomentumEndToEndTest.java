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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste vertical do histórico e momentum.
 *
 * <p>O momentum permanece MOMENTUM_V1 e continua auditando a evolução
 * do desconto CASH explícito. A ativação do SCORE_V2 não altera essa
 * semântica histórica.</p>
 *
 * <p>O score, porém, passa a utilizar BASIS_DISCOUNT.</p>
 */
@PostgresIntegrationTest
class AmazonDealProcessingMomentumEndToEndTest {

    private static final String ASIN =
        "B0MOM11E2E";

    private static final String DEALS_FIXTURE =
        "amazon/fixtures/deals/end-to-end-deal.html";

    private static final String PRODUCT_FIXTURE =
        "amazon/fixtures/product/amazon-commercial.html";

    private static final Instant FIRST_COLLECTION_INSTANT =
        Instant.parse(
            "2026-09-20T13:00:00Z"
        );

    private static final Instant SECOND_COLLECTION_INSTANT =
        Instant.parse(
            "2026-09-20T16:00:00Z"
        );

    @Test
    void shouldPersistHistoricalMomentumAcrossTwoCollections()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();


        String baseDealsHtml =
            loadFixture(
                DEALS_FIXTURE
            );

        String productHtml =
            loadFixture(
                PRODUCT_FIXTURE
            );

        String firstDealsHtml =
            dealsHtml(
                baseDealsHtml,
                62
            );

        String secondDealsHtml =
            dealsHtml(
                baseDealsHtml,
                68
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
                     ASIN,
                     firstDealsHtml,
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

            CollectionRequest request =
                new CollectionRequest(
                    URI.create(
                        server.dealsUrl()
                    )
                );

            AmazonDealProcessingService firstService =
                AmazonDealProcessingComposition.create(
                    connection,
                    Clock.fixed(
                        FIRST_COLLECTION_INSTANT,
                        ZoneOffset.UTC
                    ),
                    httpClient
                );

            var firstResult =
                firstService.process(
                    request
                );

            assertEquals(
                1,
                firstResult.size()
            );

            server.setDealsHtml(
                secondDealsHtml
            );

            AmazonDealProcessingService secondService =
                AmazonDealProcessingComposition.create(
                    connection,
                    Clock.fixed(
                        SECOND_COLLECTION_INSTANT,
                        ZoneOffset.UTC
                    ),
                    httpClient
                );

            var secondResult =
                secondService.process(
                    request
                );

            assertEquals(
                1,
                secondResult.size()
            );

            var repeatedSecondResult =
                secondService.process(
                    request
                );

            assertEquals(
                1,
                repeatedSecondResult.size()
            );

            long productId =
                findProductId(
                    connection,
                    ASIN
                );

            List<PersistedHistoricalEvaluation> evaluations =
                loadHistoricalEvaluations(
                    connection,
                    productId
                );

            assertEquals(
                2,
                evaluations.size()
            );

            PersistedHistoricalEvaluation first =
                evaluations.get(
                    0
                );

            PersistedHistoricalEvaluation second =
                evaluations.get(
                    1
                );

            /*
             * =====================================================
             * PRIMEIRA OBSERVAÇÃO
             * =====================================================
             */
            assertEquals(
                FIRST_COLLECTION_INSTANT,
                first.collectedAt()
                    .toInstant()
            );

            assertBigDecimalEquals(
                "62",
                first.soldPercentage()
            );

            assertTrue(
                first.eligible()
            );

            assertNull(
                first.rejectionReason()
            );

            assertEquals(
                "SCORE_V2",
                first.scoreVersion()
            );

            /*
             * SCORE_V2:
             *
             * sold:
             * 62 / 100 * 30 = 18.6000
             *
             * basisDiscount:
             * ((99.90 - 79.90) / 99.90) * 100 = 20.0200
             * 20.0200 / 100 * 25 = 5.0050
             *
             * rating:
             * 18.4000
             *
             * reviews:
             * 15.0000
             *
             * total:
             * 57.0050
             */
            assertBigDecimalEquals(
                "57.0050",
                first.score()
            );

            assertNull(
                first.momentum()
            );

            assertNull(
                first.momentumVersion()
            );

            assertEquals(
                "MOMENTUM_V1",
                first.auditCalculationVersion()
            );

            assertEquals(
                "UNAVAILABLE",
                first.auditStatus()
            );

            assertEquals(
                "NO_PREVIOUS_SNAPSHOT",
                first.auditUnavailableReason()
            );

            assertNull(
                first.previousOfferSnapshotId()
            );

            assertNull(
                first.elapsedSeconds()
            );

            assertNull(
                first.soldPercentageDelta()
            );

            assertNull(
                first.currentPriceDelta()
            );

            assertNull(
                first.currentPriceDeltaPercentage()
            );

            assertNull(
                first.cashDiscountDelta()
            );

            assertNull(
                first.auditMomentum()
            );

            /*
             * =====================================================
             * SEGUNDA OBSERVAÇÃO
             * =====================================================
             */
            assertEquals(
                SECOND_COLLECTION_INSTANT,
                second.collectedAt()
                    .toInstant()
            );

            assertBigDecimalEquals(
                "68",
                second.soldPercentage()
            );

            assertTrue(
                second.eligible()
            );

            assertNull(
                second.rejectionReason()
            );

            assertEquals(
                "SCORE_V2",
                second.scoreVersion()
            );

            /*
             * SCORE_V2:
             *
             * sold:
             * 68 / 100 * 30 = 20.4000
             *
             * basisDiscount:
             * 5.0050
             *
             * rating:
             * 18.4000
             *
             * reviews:
             * 15.0000
             *
             * total:
             * 58.8050
             *
             * A diferença entre os dois scores continua vindo somente
             * de soldPercentage.
             */
            assertBigDecimalEquals(
                "58.8050",
                second.score()
            );

            assertBigDecimalEquals(
                "2.0000",
                second.momentum()
            );

            assertEquals(
                "MOMENTUM_V1",
                second.momentumVersion()
            );

            assertEquals(
                "MOMENTUM_V1",
                second.auditCalculationVersion()
            );

            assertEquals(
                "AVAILABLE",
                second.auditStatus()
            );

            assertNull(
                second.auditUnavailableReason()
            );

            assertEquals(
                first.snapshotId(),
                second.previousOfferSnapshotId()
            );

            assertEquals(
                10_800L,
                second.elapsedSeconds()
            );

            assertBigDecimalEquals(
                "6",
                second.soldPercentageDelta()
            );

            assertBigDecimalEquals(
                "0",
                second.currentPriceDelta()
            );

            assertBigDecimalEquals(
                "0.0000",
                second.currentPriceDeltaPercentage()
            );

            /*
             * SnapshotEvolutionCalculator continua medindo a evolução
             * do desconto CASH explícito. A fixture mantém 25% nas
             * duas observações, portanto o delta continua zero.
             */
            assertBigDecimalEquals(
                "0",
                second.cashDiscountDelta()
            );

            assertBigDecimalEquals(
                "2.0000",
                second.auditMomentum()
            );

            assertEquals(
                4L,
                countScoreFactors(
                    connection,
                    first.evaluationId()
                )
            );

            assertEquals(
                4L,
                countScoreFactors(
                    connection,
                    second.evaluationId()
                )
            );

            assertEquals(
                2L,
                countSnapshots(
                    connection,
                    productId
                )
            );

            assertEquals(
                2L,
                countEvaluations(
                    connection,
                    productId
                )
            );

            assertEquals(
                2L,
                countMomentumAudits(
                    connection,
                    productId
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

    private String dealsHtml(
        String baseHtml,
        int soldPercentage
    ) {

        String withAsin =
            baseHtml.replace(
                "B0E2E85001",
                ASIN
            );

        return withAsin.replace(
            "\"percentClaimed\": 37",
            "\"percentClaimed\": "
                + soldPercentage
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

                long productId =
                    resultSet.getLong(
                        "id"
                    );

                assertTrue(
                    productId > 0
                );

                assertFalse(
                    resultSet.next()
                );

                return productId;
            }
        }
    }

    private List<PersistedHistoricalEvaluation>
    loadHistoricalEvaluations(
        Connection connection,
        long productId
    ) throws Exception {

        String sql = """
            SELECT
                os.id AS snapshot_id,
                os.collected_at,
                os.sold_percentage,

                de.id AS evaluation_id,
                de.eligible,
                de.rejection_reason,
                de.score,
                de.score_version,
                de.momentum,
                de.momentum_version,

                ma.calculation_version,
                ma.status AS audit_status,
                ma.unavailable_reason,
                ma.previous_offer_snapshot_id,
                ma.elapsed_seconds,
                ma.sold_percentage_delta,
                ma.current_price_delta,
                ma.current_price_delta_percentage,
                ma.cash_discount_delta,
                ma.momentum AS audit_momentum

            FROM offer_snapshot os

            JOIN deal_evaluation de
              ON de.offer_snapshot_id = os.id

            JOIN deal_evaluation_momentum_audit ma
              ON ma.deal_evaluation_id = de.id

            WHERE os.product_id = ?

            ORDER BY
                os.collected_at ASC,
                os.id ASC
            """;

        List<PersistedHistoricalEvaluation> result =
            new ArrayList<>();

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

                while (resultSet.next()) {

                    result.add(
                        new PersistedHistoricalEvaluation(
                            resultSet.getLong(
                                "snapshot_id"
                            ),
                            resultSet.getObject(
                                "collected_at",
                                OffsetDateTime.class
                            ),
                            resultSet.getBigDecimal(
                                "sold_percentage"
                            ),
                            resultSet.getLong(
                                "evaluation_id"
                            ),
                            resultSet.getBoolean(
                                "eligible"
                            ),
                            resultSet.getString(
                                "rejection_reason"
                            ),
                            resultSet.getBigDecimal(
                                "score"
                            ),
                            resultSet.getString(
                                "score_version"
                            ),
                            resultSet.getBigDecimal(
                                "momentum"
                            ),
                            resultSet.getString(
                                "momentum_version"
                            ),
                            resultSet.getString(
                                "calculation_version"
                            ),
                            resultSet.getString(
                                "audit_status"
                            ),
                            resultSet.getString(
                                "unavailable_reason"
                            ),
                            nullableLong(
                                resultSet,
                                "previous_offer_snapshot_id"
                            ),
                            nullableLong(
                                resultSet,
                                "elapsed_seconds"
                            ),
                            resultSet.getBigDecimal(
                                "sold_percentage_delta"
                            ),
                            resultSet.getBigDecimal(
                                "current_price_delta"
                            ),
                            resultSet.getBigDecimal(
                                "current_price_delta_percentage"
                            ),
                            resultSet.getBigDecimal(
                                "cash_discount_delta"
                            ),
                            resultSet.getBigDecimal(
                                "audit_momentum"
                            )
                        )
                    );
                }
            }
        }

        return List.copyOf(
            result
        );
    }

    private Long nullableLong(
        ResultSet resultSet,
        String column
    ) throws Exception {

        long value =
            resultSet.getLong(
                column
            );

        return resultSet.wasNull()
            ? null
            : value;
    }

    private long countScoreFactors(
        Connection connection,
        long evaluationId
    ) throws Exception {

        return countByLong(
            connection,
            """
            SELECT COUNT(*)
            FROM deal_evaluation_score_factor
            WHERE deal_evaluation_id = ?
            """,
            evaluationId
        );
    }

    private long countSnapshots(
        Connection connection,
        long productId
    ) throws Exception {

        return countByLong(
            connection,
            """
            SELECT COUNT(*)
            FROM offer_snapshot
            WHERE product_id = ?
            """,
            productId
        );
    }

    private long countEvaluations(
        Connection connection,
        long productId
    ) throws Exception {

        return countByLong(
            connection,
            """
            SELECT COUNT(*)
            FROM deal_evaluation de
            JOIN offer_snapshot os
              ON os.id = de.offer_snapshot_id
            WHERE os.product_id = ?
            """,
            productId
        );
    }

    private long countMomentumAudits(
        Connection connection,
        long productId
    ) throws Exception {

        return countByLong(
            connection,
            """
            SELECT COUNT(*)
            FROM deal_evaluation_momentum_audit ma
            JOIN deal_evaluation de
              ON de.id = ma.deal_evaluation_id
            JOIN offer_snapshot os
              ON os.id = de.offer_snapshot_id
            WHERE os.product_id = ?
            """,
            productId
        );
    }

    private long countByLong(
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

    private record PersistedHistoricalEvaluation(
        long snapshotId,
        OffsetDateTime collectedAt,
        BigDecimal soldPercentage,
        long evaluationId,
        boolean eligible,
        String rejectionReason,
        BigDecimal score,
        String scoreVersion,
        BigDecimal momentum,
        String momentumVersion,
        String auditCalculationVersion,
        String auditStatus,
        String auditUnavailableReason,
        Long previousOfferSnapshotId,
        Long elapsedSeconds,
        BigDecimal soldPercentageDelta,
        BigDecimal currentPriceDelta,
        BigDecimal currentPriceDeltaPercentage,
        BigDecimal cashDiscountDelta,
        BigDecimal auditMomentum
    ) {
    }

    private static final class LocalAmazonServer
        implements AutoCloseable {

        private final HttpServer server;

        private final ExecutorService executor;

        private final AtomicReference<String> dealsHtml;

        private LocalAmazonServer(
            HttpServer server,
            ExecutorService executor,
            AtomicReference<String> dealsHtml
        ) {

            this.server =
                server;

            this.executor =
                executor;

            this.dealsHtml =
                dealsHtml;
        }

        static LocalAmazonServer start(
            String asin,
            String initialDealsHtml,
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

            AtomicReference<String> dealsHtml =
                new AtomicReference<>(
                    initialDealsHtml
                );

            server.createContext(
                "/deals",
                exchange ->
                    respond(
                        exchange,
                        200,
                        dealsHtml.get()
                    )
            );

            server.createContext(
                "/product/" + asin,
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
                executor,
                dealsHtml
            );
        }

        void setDealsHtml(
            String html
        ) {

            dealsHtml.set(
                html
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
