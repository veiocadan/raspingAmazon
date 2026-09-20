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
 * Teste vertical específico da FASE 11.
 *
 * <p>Exercita duas observações temporais do mesmo ASIN pelo fluxo
 * completo de produção:</p>
 *
 * <pre>
 * primeira coleta
 *     ↓
 * OfferSnapshot 62%
 *     ↓
 * DealEvaluation
 *     ↓
 * momentum indisponível
 *     ↓
 * audit = NO_PREVIOUS_SNAPSHOT
 *
 * segunda coleta, três horas depois
 *     ↓
 * OfferSnapshot 68%
 *     ↓
 * histórico anterior
 *     ↓
 * SnapshotEvolution
 *     ↓
 * delta vendido = +6 p.p.
 *     ↓
 * MOMENTUM_V1 = 2.0000 p.p./h
 *     ↓
 * DealEvaluation
 *     ↓
 * MomentumAudit
 *     ↓
 * PostgreSQL
 * </pre>
 *
 * <p>O teste também reprocessa a segunda observação para garantir
 * que a integração de momentum preserva a identidade idempotente
 * do snapshot e não duplica avaliações ou auditorias.</p>
 */
class AmazonDealProcessingMomentumEndToEndTest {

    private static final String ASIN =
        "B0MOM11E2E";

    private static final String DEALS_FIXTURE =
        "amazon/fixtures/deals/end-to-end-deal.html";

    private static final String PRODUCT_FIXTURE =
        "amazon/fixtures/product/amazon-commercial.html";

    /*
     * 13:00 UTC = 10:00 no horário -03:00.
     */
    private static final Instant FIRST_COLLECTION_INSTANT =
        Instant.parse(
            "2026-09-20T13:00:00Z"
        );

    /*
     * Três horas depois.
     */
    private static final Instant SECOND_COLLECTION_INSTANT =
        Instant.parse(
            "2026-09-20T16:00:00Z"
        );

    @Test
    void shouldPersistHistoricalMomentumAcrossTwoCollections()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        DatabaseMigration.migrate(
            config
        );

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

            /*
             * =====================================================
             * PRIMEIRA COLETA
             * =====================================================
             *
             * soldPercentage = 62
             *
             * Não existe histórico anterior.
             */
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

            /*
             * =====================================================
             * SEGUNDA COLETA
             * =====================================================
             *
             * soldPercentage = 68
             *
             * O servidor passa a devolver a segunda versão do
             * mesmo negócio.
             */
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

            /*
             * =====================================================
             * REPROCESSAMENTO DA SEGUNDA OBSERVAÇÃO
             * =====================================================
             *
             * Mesmo ASIN
             * mesmo collectedAt
             * mesma source
             *
             * Portanto deve reutilizar o OfferSnapshot existente.
             */
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

            /*
             * O reprocessamento não cria terceiro snapshot.
             */
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
                "SCORE_V1",
                first.scoreVersion()
            );

            /*
             * SCORE_V1:
             *
             * sold:
             * 62 / 100 * 30 = 18.6000
             *
             * cash:
             * 25 / 100 * 25 = 6.2500
             *
             * rating:
             * 4.6 / 5 * 100 = 92
             * 92 / 100 * 20 = 18.4000
             *
             * reviews:
             * 15.0000
             *
             * total:
             * 58.2500
             */
            assertBigDecimalEquals(
                "58.2500",
                first.score()
            );

            /*
             * Primeira observação não possui momentum agregado.
             */
            assertNull(
                first.momentum()
            );

            assertNull(
                first.momentumVersion()
            );

            /*
             * Porém existe auditoria da tentativa.
             */
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

            /*
             * Momentum não altera a elegibilidade.
             */
            assertTrue(
                second.eligible()
            );

            assertNull(
                second.rejectionReason()
            );

            /*
             * Momentum também não altera a versão do score.
             */
            assertEquals(
                "SCORE_V1",
                second.scoreVersion()
            );

            /*
             * SCORE_V1 da segunda observação:
             *
             * sold:
             * 68 / 100 * 30 = 20.4000
             *
             * cash:
             * 6.2500
             *
             * rating:
             * 18.4000
             *
             * reviews:
             * 15.0000
             *
             * total:
             * 60.0500
             *
             * A diferença para o primeiro score ocorre apenas porque
             * o fato atual soldPercentage mudou de 62 para 68.
             * Momentum não participa do SCORE_V1.
             */
            assertBigDecimalEquals(
                "60.0500",
                second.score()
            );

            /*
             * 62 -> 68
             *
             * +6 p.p.
             *
             * 3 horas
             *
             * 6 / 3 = 2 p.p./h
             */
            assertBigDecimalEquals(
                "2.0000",
                second.momentum()
            );

            assertEquals(
                "MOMENTUM_V1",
                second.momentumVersion()
            );

            /*
             * Auditoria completa do cálculo.
             */
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

            /*
             * O preço permaneceu 79.90 nas duas observações.
             */
            assertBigDecimalEquals(
                "0",
                second.currentPriceDelta()
            );

            assertBigDecimalEquals(
                "0.0000",
                second.currentPriceDeltaPercentage()
            );

            /*
             * O desconto comercial explícito permaneceu 25%.
             */
            assertBigDecimalEquals(
                "0",
                second.cashDiscountDelta()
            );

            assertBigDecimalEquals(
                "2.0000",
                second.auditMomentum()
            );

            /*
             * =====================================================
             * SCORE CONTINUA AUDITÁVEL
             * =====================================================
             *
             * Cada avaliação continua possuindo os quatro fatores
             * da FASE 10.
             */
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

            /*
             * =====================================================
             * IDEMPOTÊNCIA
             * =====================================================
             */
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

    /**
     * Produz uma versão controlada do fixture de Deals.
     *
     * <p>Reutilizamos a estrutura validada pela FASE 10 e alteramos
     * apenas:</p>
     *
     * <ul>
     *     <li>ASIN;</li>
     *     <li>percentClaimed.</li>
     * </ul>
     */
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

    /**
     * Carrega snapshot, avaliação e auditoria de momentum em ordem
     * cronológica.
     */
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

    /**
     * Limpa todos os dados produzidos pelo ASIN de teste.
     *
     * <p>deal_evaluation_momentum_audit é removida pelo
     * ON DELETE CASCADE existente em sua FK para deal_evaluation.</p>
     */
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

        /*
         * deal_evaluation_rule_result,
         * deal_evaluation_score_factor e
         * deal_evaluation_momentum_audit
         *
         * são removidos por ON DELETE CASCADE.
         */
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

    /**
     * Servidor Amazon local controlado pelo teste.
     *
     * <p>O HTML da página Deals pode mudar durante a execução.
     * A página individual do produto permanece estável.</p>
     */
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
