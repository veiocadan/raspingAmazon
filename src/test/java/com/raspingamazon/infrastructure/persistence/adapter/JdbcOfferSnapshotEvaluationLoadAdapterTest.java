package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.commercial.PaymentConditionType;
import com.raspingamazon.domain.commercial.PaymentMethod;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcOfferSnapshotEvaluationLoadAdapterTest {

    private static final String ASIN =
        "B0EVAL0001";

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-22T20:30:00-03:00"
        );

    private static final String SOURCE =
        "https://www.amazon.com.br/deals";

    @Test
    void shouldReconstructCompleteSnapshotForEvaluation()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        TestData data =
            null;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            data =
                createCompleteSnapshot(
                    connection
                );

            JdbcOfferSnapshotEvaluationLoadAdapter adapter =
                new JdbcOfferSnapshotEvaluationLoadAdapter(
                    connection
                );

            OfferSnapshot snapshot =
                adapter.findById(
                        data.snapshotId
                    )
                    .orElseThrow();

            assertEquals(
                data.snapshotId,
                snapshot.id()
            );

            assertEquals(
                ASIN,
                snapshot.product()
                    .asin()
                    .value()
            );

            assertEquals(
                SellerType.AMAZON,
                snapshot.sellerType()
            );

            assertEquals(
                DeliveryType.AMAZON,
                snapshot.deliveryType()
            );

            assertEquals(
                new BigDecimal(
                    "99.90"
                ),
                snapshot.currentPrice()
                    .amount()
            );

            assertEquals(
                new BigDecimal(
                    "129.90"
                ),
                snapshot.basisPrice()
                    .amount()
            );

            assertEquals(
                1,
                snapshot.paymentConditions()
                    .size()
            );

            PaymentCondition condition =
                snapshot.paymentConditions()
                    .getFirst();

            assertEquals(
                PaymentConditionType.CASH,
                condition.type()
            );

            assertEquals(
                new BigDecimal(
                    "94.90"
                ),
                condition.price()
                    .amount()
            );

            assertEquals(
                new BigDecimal(
                    "5.00"
                ),
                condition.discountPercentage()
                    .value()
            );

            assertEquals(
                1,
                condition.paymentMethods()
                    .size()
            );

            assertEquals(
                PaymentMethod.PIX,
                condition.paymentMethods()
                    .getFirst()
            );

        } finally {

            deleteTestData(
                config,
                data
            );
        }
    }

    @Test
    void shouldReturnEmptyWhenSnapshotDoesNotExist()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcOfferSnapshotEvaluationLoadAdapter adapter =
                new JdbcOfferSnapshotEvaluationLoadAdapter(
                    connection
                );

            Optional<OfferSnapshot> result =
                adapter.findById(
                    Long.MAX_VALUE
                );

            assertTrue(
                result.isEmpty()
            );
        }
    }

    @Test
    void shouldRejectSnapshotWithoutNormalizedEvidence()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        TestData data =
            null;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            long productId =
                insertProduct(
                    connection
                );

            long snapshotId =
                insertSnapshot(
                    connection,
                    productId
                );

            data =
                new TestData(
                    productId,
                    snapshotId,
                    0L
                );

            JdbcOfferSnapshotEvaluationLoadAdapter adapter =
                new JdbcOfferSnapshotEvaluationLoadAdapter(
                    connection
                );

            assertThrows(
                IllegalStateException.class,
                () -> adapter.findById(
                    snapshotId
                )
            );

        } finally {

            deleteTestData(
                config,
                data
            );
        }
    }

    @Test
    void shouldRejectDuplicateEvaluationForSameSnapshot()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        TestData data =
            null;

        long evaluationId =
            0L;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            data =
                createCompleteSnapshot(
                    connection
                );

            evaluationId =
                insertEvaluation(
                    connection,
                    data.snapshotId
                );

            long finalSnapshotId =
                data.snapshotId;

            assertThrows(
                SQLException.class,
                () -> insertEvaluation(
                    connection,
                    finalSnapshotId
                )
            );

        } finally {

            deleteEvaluation(
                config,
                evaluationId
            );

            deleteTestData(
                config,
                data
            );
        }
    }

    private TestData createCompleteSnapshot(
        Connection connection
    ) throws Exception {

        long productId =
            insertProduct(
                connection
            );

        long snapshotId =
            insertSnapshot(
                connection,
                productId
            );

        insertEvidence(
            connection,
            snapshotId,
            "SELLER",
            "Amazon.com.br",
            SellerType.AMAZON.name(),
            "merchantInfoFeature"
        );

        insertEvidence(
            connection,
            snapshotId,
            "DELIVERY",
            "Amazon.com.br",
            DeliveryType.AMAZON.name(),
            "fulfillerInfoFeature"
        );

        long paymentConditionId =
            insertCashPaymentCondition(
                connection,
                snapshotId
            );

        insertPaymentMethod(
            connection,
            paymentConditionId,
            PaymentMethod.PIX
        );

        return new TestData(
            productId,
            snapshotId,
            paymentConditionId
        );
    }

    private long insertProduct(
        Connection connection
    ) throws Exception {

        String sql =
            """
            INSERT INTO product (
                asin,
                title,
                image_url,
                product_url
            )
            VALUES (?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                ASIN
            );

            statement.setString(
                2,
                "Produto avaliação"
            );

            statement.setString(
                3,
                "https://example.invalid/evaluation.jpg"
            );

            statement.setString(
                4,
                "https://www.amazon.com.br/dp/" + ASIN
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "Product insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private long insertSnapshot(
        Connection connection,
        long productId
    ) throws Exception {

        String sql =
            """
            INSERT INTO offer_snapshot (
                product_id,
                collected_at,
                current_price,
                basis_price,
                previous_price,
                sold_percentage,
                rating,
                review_count,
                seller_name,
                delivery_provider,
                source
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                productId
            );

            statement.setObject(
                2,
                COLLECTED_AT
            );

            statement.setBigDecimal(
                3,
                new BigDecimal(
                    "99.90"
                )
            );

            statement.setBigDecimal(
                4,
                new BigDecimal(
                    "129.90"
                )
            );

            statement.setBigDecimal(
                5,
                new BigDecimal(
                    "119.90"
                )
            );

            statement.setBigDecimal(
                6,
                new BigDecimal(
                    "42.00"
                )
            );

            statement.setDouble(
                7,
                4.6
            );

            statement.setLong(
                8,
                58363L
            );

            statement.setString(
                9,
                "Amazon.com.br"
            );

            statement.setString(
                10,
                "Amazon.com.br"
            );

            statement.setString(
                11,
                SOURCE
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "OfferSnapshot insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private void insertEvidence(
        Connection connection,
        long snapshotId,
        String evidenceType,
        String rawValue,
        String normalizedValue,
        String sourceComponent
    ) throws Exception {

        String sql =
            """
            INSERT INTO offer_evidence (
                offer_snapshot_id,
                evidence_type,
                raw_value,
                normalized_value,
                source_adapter,
                source_component,
                observed_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            statement.setString(
                2,
                evidenceType
            );

            statement.setString(
                3,
                rawValue
            );

            statement.setString(
                4,
                normalizedValue
            );

            statement.setString(
                5,
                "test-enrichment"
            );

            statement.setString(
                6,
                sourceComponent
            );

            statement.setObject(
                7,
                COLLECTED_AT.plusMinutes(
                    1
                )
            );

            statement.executeUpdate();
        }
    }

    private long insertCashPaymentCondition(
        Connection connection,
        long snapshotId
    ) throws Exception {

        String sql =
            """
            INSERT INTO offer_payment_condition (
                offer_snapshot_id,
                condition_type,
                price,
                discount_percentage,
                installment_count,
                installment_amount,
                installment_total,
                interest
            )
            VALUES (?, ?, ?, ?, NULL, NULL, NULL, NULL)
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            statement.setString(
                2,
                PaymentConditionType.CASH.name()
            );

            statement.setBigDecimal(
                3,
                new BigDecimal(
                    "94.90"
                )
            );

            statement.setBigDecimal(
                4,
                new BigDecimal(
                    "5.00"
                )
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "PaymentCondition insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private void insertPaymentMethod(
        Connection connection,
        long paymentConditionId,
        PaymentMethod paymentMethod
    ) throws Exception {

        String sql =
            """
            INSERT INTO offer_payment_condition_method (
                payment_condition_id,
                payment_method
            )
            VALUES (?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                paymentConditionId
            );

            statement.setString(
                2,
                paymentMethod.name()
            );

            statement.executeUpdate();
        }
    }

    private long insertEvaluation(
        Connection connection,
        long snapshotId
    ) throws SQLException {

        String sql =
            """
            INSERT INTO deal_evaluation (
                offer_snapshot_id,
                eligible,
                rejection_reason,
                eligibility_policy_version,
                filter_profile_version,
                score,
                score_version,
                momentum,
                momentum_version,
                evaluated_at
            )
            VALUES (
                ?,
                FALSE,
                NULL,
                'TEST_POLICY',
                'TEST_FILTER',
                NULL,
                NULL,
                NULL,
                NULL,
                ?
            )
            RETURNING id
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                snapshotId
            );

            statement.setObject(
                2,
                COLLECTED_AT.plusMinutes(
                    2
                )
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new SQLException(
                        "DealEvaluation insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private void deleteEvaluation(
        ApplicationConfig config,
        long evaluationId
    ) {

        if (evaluationId <= 0) {
            return;
        }

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 );
             PreparedStatement statement =
                 connection.prepareStatement(
                     """
                     DELETE FROM deal_evaluation
                     WHERE id = ?
                     """
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            statement.executeUpdate();

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean DealEvaluation test data",
                exception
            );
        }
    }

    private void deleteTestData(
        ApplicationConfig config,
        TestData data
    ) {

        if (data == null) {
            return;
        }

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM deal_evaluation_momentum_audit
                         WHERE deal_evaluation_id IN (
                             SELECT id
                             FROM deal_evaluation
                             WHERE offer_snapshot_id = ?
                         )
                         """
                     )) {

                statement.setLong(
                    1,
                    data.snapshotId
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM deal_evaluation_rule_result
                         WHERE deal_evaluation_id IN (
                             SELECT id
                             FROM deal_evaluation
                             WHERE offer_snapshot_id = ?
                         )
                         """
                     )) {

                statement.setLong(
                    1,
                    data.snapshotId
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM deal_evaluation_score_factor
                         WHERE deal_evaluation_id IN (
                             SELECT id
                             FROM deal_evaluation
                             WHERE offer_snapshot_id = ?
                         )
                         """
                     )) {

                statement.setLong(
                    1,
                    data.snapshotId
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM deal_evaluation
                         WHERE offer_snapshot_id = ?
                         """
                     )) {

                statement.setLong(
                    1,
                    data.snapshotId
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM processing_job
                         WHERE offer_snapshot_id = ?
                         """
                     )) {

                statement.setLong(
                    1,
                    data.snapshotId
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM offer_payment_condition_method
                         WHERE payment_condition_id IN (
                             SELECT id
                             FROM offer_payment_condition
                             WHERE offer_snapshot_id = ?
                         )
                         """
                     )) {

                statement.setLong(
                    1,
                    data.snapshotId
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM offer_payment_condition
                         WHERE offer_snapshot_id = ?
                         """
                     )) {

                statement.setLong(
                    1,
                    data.snapshotId
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM offer_evidence
                         WHERE offer_snapshot_id = ?
                         """
                     )) {

                statement.setLong(
                    1,
                    data.snapshotId
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM offer_snapshot
                         WHERE id = ?
                         """
                     )) {

                statement.setLong(
                    1,
                    data.snapshotId
                );

                statement.executeUpdate();
            }

            try (PreparedStatement statement =
                     connection.prepareStatement(
                         """
                         DELETE FROM product
                         WHERE id = ?
                         """
                     )) {

                statement.setLong(
                    1,
                    data.productId
                );

                statement.executeUpdate();
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean evaluation loader test data",
                exception
            );
        }
    }

    private record TestData(
        long productId,
        long snapshotId,
        long paymentConditionId
    ) {
    }
}
