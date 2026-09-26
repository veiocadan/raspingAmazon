package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcDealEvaluationLookupAdapterTest {

    private static final String ASIN =
        "B0EVLOOK01";

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-22T21:00:00-03:00"
        );

    private static final String SOURCE =
        "https://www.amazon.com.br/deals";

    @Test
    void shouldFindEvaluationByOfferSnapshotId()
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

            long evaluationId =
                insertEvaluation(
                    connection,
                    snapshotId
                );

            data =
                new TestData(
                    productId,
                    snapshotId,
                    evaluationId
                );

            JdbcDealEvaluationLookupAdapter adapter =
                new JdbcDealEvaluationLookupAdapter(
                    connection
                );

            OptionalLong result =
                adapter.findEvaluationIdByOfferSnapshotId(
                    snapshotId
                );

            assertTrue(
                result.isPresent()
            );

            assertEquals(
                evaluationId,
                result.getAsLong()
            );

        } finally {

            deleteTestData(
                config,
                data
            );
        }
    }

    @Test
    void shouldReturnEmptyWhenEvaluationDoesNotExist()
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

            JdbcDealEvaluationLookupAdapter adapter =
                new JdbcDealEvaluationLookupAdapter(
                    connection
                );

            OptionalLong result =
                adapter.findEvaluationIdByOfferSnapshotId(
                    snapshotId
                );

            assertTrue(
                result.isEmpty()
            );

        } finally {

            deleteTestData(
                config,
                data
            );
        }
    }

    @Test
    void shouldRejectInvalidSnapshotId()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcDealEvaluationLookupAdapter adapter =
                new JdbcDealEvaluationLookupAdapter(
                    connection
                );

            assertThrows(
                IllegalArgumentException.class,
                () ->
                    adapter.findEvaluationIdByOfferSnapshotId(
                        0L
                    )
            );
        }
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
                "Produto lookup avaliação"
            );

            statement.setString(
                3,
                "https://example.invalid/evaluation-lookup.jpg"
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

    private long insertEvaluation(
        Connection connection,
        long snapshotId
    ) throws Exception {

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
                    1
                )
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "DealEvaluation insert returned no id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
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
                "Could not clean DealEvaluation lookup test data",
                exception
            );
        }
    }

    private record TestData(
        long productId,
        long snapshotId,
        long evaluationId
    ) {
    }
}
