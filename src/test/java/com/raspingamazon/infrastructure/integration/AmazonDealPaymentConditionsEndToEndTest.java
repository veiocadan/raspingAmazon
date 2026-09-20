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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste vertical das condições comerciais introduzidas no pipeline
 * durante a FASE 9-C1.5.
 *
 * <p>Exercita de forma hermética:</p>
 *
 * <pre>
 * fixture Deals
 *     ↓
 * coleta HTTP local
 *     ↓
 * parser de Deals
 *     ↓
 * fixture de produto comercial
 *     ↓
 * enrichment
 *     ↓
 * AmazonPaymentConditionParser
 *     ↓
 * ProductEnrichmentResult.paymentConditions
 *     ↓
 * OfferSnapshot
 *     ↓
 * PaymentConditionPersistencePort
 *     ↓
 * offer_payment_condition
 *     ↓
 * offer_payment_condition_method
 * </pre>
 *
 * <p>O teste não acessa a Amazon real.</p>
 */
class AmazonDealPaymentConditionsEndToEndTest {

    private static final String ASIN =
        "B0PAY90001";

    private static final String DEALS_FIXTURE =
        "amazon/fixtures/deals/payment-conditions-end-to-end-deal.html";

    private static final String PRODUCT_FIXTURE =
        "amazon/fixtures/product/amazon-commercial.html";

    private static final Instant COLLECTION_INSTANT =
        Instant.parse(
            "2026-09-20T19:45:00Z"
        );

    @Test
    void shouldPersistCashAndCreditConditionsFromProductPage()
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

            var results =
                service.process(
                    request
                );

            assertEquals(
                1,
                results.size()
            );

            assertEquals(
                ASIN,
                results.getFirst()
                    .product()
                    .asin()
                    .value()
            );

            assertEquals(
                2,
                results.getFirst()
                    .offerSnapshot()
                    .paymentConditions()
                    .size()
            );

            long productId =
                findProductId(
                    connection,
                    ASIN
                );

            long snapshotId =
                findSnapshotId(
                    connection,
                    productId
                );

            List<PaymentConditionRow> rows =
                loadPaymentConditionRows(
                    connection,
                    snapshotId
                );

            /*
             * Esperamos três linhas no JOIN:
             *
             * CASH + PIX
             * CASH + NUPAY_ADDITIONAL_LIMIT
             * CREDIT_INSTALLMENT + CREDIT_CARD
             */
            assertEquals(
                3,
                rows.size()
            );

            assertCashPixRow(
                rows.get(0)
            );

            assertCashNuPayRow(
                rows.get(1)
            );

            assertCreditCardRow(
                rows.get(2)
            );

            assertEquals(
                2L,
                countPaymentConditions(
                    connection,
                    snapshotId
                )
            );

            assertEquals(
                3L,
                countPaymentMethods(
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

    private void assertCashPixRow(
        PaymentConditionRow row
    ) {

        assertEquals(
            "CASH",
            row.conditionType()
        );

        assertBigDecimalEquals(
            "79.90",
            row.price()
        );

        assertBigDecimalEquals(
            "25",
            row.discountPercentage()
        );

        assertNull(
            row.installmentCount()
        );

        assertNull(
            row.installmentAmount()
        );

        assertNull(
            row.installmentTotal()
        );

        assertNull(
            row.interest()
        );

        assertEquals(
            "PIX",
            row.paymentMethod()
        );
    }

    private void assertCashNuPayRow(
        PaymentConditionRow row
    ) {

        assertEquals(
            "CASH",
            row.conditionType()
        );

        assertBigDecimalEquals(
            "79.90",
            row.price()
        );

        assertBigDecimalEquals(
            "25",
            row.discountPercentage()
        );

        assertEquals(
            "NUPAY_ADDITIONAL_LIMIT",
            row.paymentMethod()
        );
    }

    private void assertCreditCardRow(
        PaymentConditionRow row
    ) {

        assertEquals(
            "CREDIT_INSTALLMENT",
            row.conditionType()
        );

        assertNull(
            row.price()
        );

        assertNull(
            row.discountPercentage()
        );

        assertEquals(
            10,
            row.installmentCount()
        );

        assertBigDecimalEquals(
            "9.99",
            row.installmentAmount()
        );

        assertBigDecimalEquals(
            "99.90",
            row.installmentTotal()
        );

        assertBigDecimalEquals(
            "0",
            row.interest()
        );

        assertEquals(
            "CREDIT_CARD",
            row.paymentMethod()
        );
    }

    /**
     * Carrega cada condição junto de cada método persistido.
     *
     * A condição CASH possui dois métodos, portanto gera duas linhas
     * no resultado do JOIN.
     */
    private List<PaymentConditionRow> loadPaymentConditionRows(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql = """
                SELECT
                    opc.condition_type,
                    opc.price,
                    opc.discount_percentage,
                    opc.installment_count,
                    opc.installment_amount,
                    opc.installment_total,
                    opc.interest,
                    opcm.payment_method
                FROM offer_payment_condition opc
                JOIN offer_payment_condition_method opcm
                  ON opcm.payment_condition_id = opc.id
                WHERE opc.offer_snapshot_id = ?
                ORDER BY
                    opc.id,
                    opcm.id
                """;

        List<PaymentConditionRow> rows =
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

                    Integer installmentCount =
                        null;

                    int rawInstallmentCount =
                        resultSet.getInt(
                            "installment_count"
                        );

                    if (!resultSet.wasNull()) {
                        installmentCount =
                            rawInstallmentCount;
                    }

                    rows.add(
                        new PaymentConditionRow(
                            resultSet.getString(
                                "condition_type"
                            ),
                            resultSet.getBigDecimal(
                                "price"
                            ),
                            resultSet.getBigDecimal(
                                "discount_percentage"
                            ),
                            installmentCount,
                            resultSet.getBigDecimal(
                                "installment_amount"
                            ),
                            resultSet.getBigDecimal(
                                "installment_total"
                            ),
                            resultSet.getBigDecimal(
                                "interest"
                            ),
                            resultSet.getString(
                                "payment_method"
                            )
                        )
                    );
                }
            }
        }

        return List.copyOf(
            rows
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

    private long findSnapshotId(
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

                assertTrue(
                    !resultSet.next()
                );

                return snapshotId;
            }
        }
    }

    private long countPaymentConditions(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql = """
                SELECT COUNT(*)
                FROM offer_payment_condition
                WHERE offer_snapshot_id = ?
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

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    private long countPaymentMethods(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql = """
                SELECT COUNT(*)
                FROM offer_payment_condition_method opcm
                JOIN offer_payment_condition opc
                  ON opc.id = opcm.payment_condition_id
                WHERE opc.offer_snapshot_id = ?
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

                return resultSet.getLong(
                    1
                );
            }
        }
    }

    /**
     * Compara BigDecimal por valor numérico e não por escala.
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
     * Remove exclusivamente os registros pertencentes ao ASIN
     * utilizado neste teste.
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

    private record PaymentConditionRow(
        String conditionType,
        BigDecimal price,
        BigDecimal discountPercentage,
        Integer installmentCount,
        BigDecimal installmentAmount,
        BigDecimal installmentTotal,
        BigDecimal interest,
        String paymentMethod
    ) {
    }

    /**
     * Servidor HTTP local que simula:
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
                "/product/B0PAY90001",
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
