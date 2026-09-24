package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.evaluation.DealEvaluationDetail;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcDealEvaluationOperationalDetailQueryAdapterTest {

    private static final OffsetDateTime BASE_TIME =
        OffsetDateTime.parse(
            "2099-02-10T12:00:00-03:00"
        );

    @Test
    void shouldLoadCompleteEligibleEvaluationDetail()
        throws Exception {

        inTransaction(
            connection -> {

                long productId =
                    insertProduct(
                        connection,
                        "B0DET14003",
                        "Produto detalhado"
                    );

                long previousSnapshotId =
                    insertSnapshot(
                        connection,
                        productId,
                        BASE_TIME.minusHours(
                            1
                        )
                    );

                long currentSnapshotId =
                    insertSnapshot(
                        connection,
                        productId,
                        BASE_TIME
                    );

                long evaluationId =
                    insertEvaluation(
                        connection,
                        currentSnapshotId,
                        true,
                        null,
                        new BigDecimal(
                            "28.0000"
                        ),
                        new BigDecimal(
                            "12.5000"
                        )
                    );

                insertRuleResult(
                    connection,
                    evaluationId,
                    0,
                    "SELLER_IS_AMAZON",
                    true,
                    "AMAZON",
                    "AMAZON",
                    null
                );

                insertRuleResult(
                    connection,
                    evaluationId,
                    1,
                    "DELIVERY_IS_AMAZON",
                    true,
                    "AMAZON",
                    "AMAZON",
                    null
                );

                insertScoreFactor(
                    connection,
                    evaluationId,
                    0,
                    "RATING",
                    "AVAILABLE",
                    new BigDecimal(
                        "4.5000"
                    ),
                    new BigDecimal(
                        "90.0000"
                    ),
                    new BigDecimal(
                        "20.0000"
                    ),
                    new BigDecimal(
                        "18.0000"
                    )
                );

                insertScoreFactor(
                    connection,
                    evaluationId,
                    1,
                    "REVIEW_COUNT",
                    "AVAILABLE",
                    new BigDecimal(
                        "500.0000"
                    ),
                    new BigDecimal(
                        "50.0000"
                    ),
                    new BigDecimal(
                        "20.0000"
                    ),
                    new BigDecimal(
                        "10.0000"
                    )
                );

                insertAvailableMomentumAudit(
                    connection,
                    evaluationId,
                    previousSnapshotId
                );

                JdbcDealEvaluationOperationalDetailQueryAdapter adapter =
                    new JdbcDealEvaluationOperationalDetailQueryAdapter(
                        connection
                    );

                DealEvaluationDetail detail =
                    adapter.findById(
                            evaluationId
                        )
                        .orElseThrow();

                assertEquals(
                    evaluationId,
                    detail.summary()
                        .evaluationId()
                );

                assertEquals(
                    "B0DET14003",
                    detail.summary()
                        .asin()
                        .value()
                );

                assertEquals(
                    "Produto detalhado",
                    detail.summary()
                        .title()
                );

                assertEquals(
                    new BigDecimal(
                        "99.90"
                    ),
                    detail.summary()
                        .currentPrice()
                        .amount()
                );

                assertEquals(
                    new BigDecimal(
                        "129.90"
                    ),
                    detail.basisPrice()
                        .amount()
                );

                assertEquals(
                    new BigDecimal(
                        "119.90"
                    ),
                    detail.previousPrice()
                        .amount()
                );

                assertEquals(
                    new BigDecimal(
                        "42.00"
                    ),
                    detail.soldPercentage()
                );

                assertEquals(
                    4.5,
                    detail.rating()
                );

                assertEquals(
                    800L,
                    detail.reviewCount()
                );

                assertEquals(
                    "TEST_ELIGIBILITY",
                    detail.eligibilityPolicyVersion()
                );

                assertEquals(
                    "TEST_FILTER",
                    detail.filterProfileVersion()
                );

                assertEquals(
                    "TEST_SCORE",
                    detail.scoreVersion()
                );

                assertEquals(
                    "TEST_MOMENTUM",
                    detail.momentumVersion()
                );

                assertEquals(
                    2,
                    detail.ruleResults()
                        .size()
                );

                assertEquals(
                    0,
                    detail.ruleResults()
                        .get(0)
                        .ruleOrder()
                );

                assertEquals(
                    1,
                    detail.ruleResults()
                        .get(1)
                        .ruleOrder()
                );

                assertEquals(
                    2,
                    detail.scoreFactors()
                        .size()
                );

                assertEquals(
                    "RATING",
                    detail.scoreFactors()
                        .get(0)
                        .factorCode()
                );

                assertEquals(
                    "REVIEW_COUNT",
                    detail.scoreFactors()
                        .get(1)
                        .factorCode()
                );

                assertTrue(
                    detail.momentumAudit()
                        != null
                );

                assertEquals(
                    "AVAILABLE",
                    detail.momentumAudit()
                        .status()
                );

                assertEquals(
                    previousSnapshotId,
                    detail.momentumAudit()
                        .previousOfferSnapshotId()
                );

                assertEquals(
                    new BigDecimal(
                        "12.5000"
                    ),
                    detail.momentumAudit()
                        .momentum()
                );
            }
        );
    }

    @Test
    void shouldLoadIneligibleEvaluationWithUnavailableMomentum()
        throws Exception {

        inTransaction(
            connection -> {

                long productId =
                    insertProduct(
                        connection,
                        "B0DET14004",
                        "Produto rejeitado"
                    );

                long snapshotId =
                    insertSnapshot(
                        connection,
                        productId,
                        BASE_TIME.plusDays(
                            1
                        )
                    );

                long evaluationId =
                    insertEvaluation(
                        connection,
                        snapshotId,
                        false,
                        RejectionReason.RATING_BELOW_MINIMUM,
                        null,
                        null
                    );

                insertRuleResult(
                    connection,
                    evaluationId,
                    0,
                    "SELLER_IS_AMAZON",
                    true,
                    "AMAZON",
                    "AMAZON",
                    null
                );

                insertRuleResult(
                    connection,
                    evaluationId,
                    1,
                    "RATING_MINIMUM",
                    false,
                    "3.9000",
                    "4.3000",
                    RejectionReason.RATING_BELOW_MINIMUM.name()
                );

                insertUnavailableMomentumAudit(
                    connection,
                    evaluationId
                );

                JdbcDealEvaluationOperationalDetailQueryAdapter adapter =
                    new JdbcDealEvaluationOperationalDetailQueryAdapter(
                        connection
                    );

                DealEvaluationDetail detail =
                    adapter.findById(
                            evaluationId
                        )
                        .orElseThrow();

                assertFalse(
                    detail.summary()
                        .eligible()
                );

                assertEquals(
                    RejectionReason.RATING_BELOW_MINIMUM,
                    detail.summary()
                        .rejectionReason()
                );

                assertNull(
                    detail.summary()
                        .score()
                );

                assertTrue(
                    detail.scoreFactors()
                        .isEmpty()
                );

                assertEquals(
                    2,
                    detail.ruleResults()
                        .size()
                );

                assertEquals(
                    RejectionReason.RATING_BELOW_MINIMUM.name(),
                    detail.ruleResults()
                        .get(1)
                        .reasonCode()
                );

                assertEquals(
                    "UNAVAILABLE",
                    detail.momentumAudit()
                        .status()
                );

                assertEquals(
                    "NO_PREVIOUS_SNAPSHOT",
                    detail.momentumAudit()
                        .unavailableReason()
                );

                assertNull(
                    detail.momentumAudit()
                        .previousOfferSnapshotId()
                );

                assertNull(
                    detail.momentumAudit()
                        .momentum()
                );
            }
        );
    }

    @Test
    void shouldReturnEmptyWhenEvaluationDoesNotExist()
        throws Exception {

        inTransaction(
            connection -> {

                JdbcDealEvaluationOperationalDetailQueryAdapter adapter =
                    new JdbcDealEvaluationOperationalDetailQueryAdapter(
                        connection
                    );

                Optional<DealEvaluationDetail> result =
                    adapter.findById(
                        Long.MAX_VALUE
                    );

                assertTrue(
                    result.isEmpty()
                );
            }
        );
    }

    @Test
    void shouldRejectNonPositiveEvaluationId()
        throws Exception {

        inTransaction(
            connection -> {

                JdbcDealEvaluationOperationalDetailQueryAdapter adapter =
                    new JdbcDealEvaluationOperationalDetailQueryAdapter(
                        connection
                    );

                assertThrows(
                    IllegalArgumentException.class,
                    () -> adapter.findById(
                        0L
                    )
                );

                assertThrows(
                    IllegalArgumentException.class,
                    () -> adapter.findById(
                        -1L
                    )
                );
            }
        );
    }

    private void inTransaction(
        TransactionTest transactionTest
    ) throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        DatabaseMigration.migrate(
            config
        );

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                transactionTest.execute(
                    connection
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private long insertProduct(
        Connection connection,
        String asin,
        String title
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
                asin
            );

            statement.setString(
                2,
                title
            );

            statement.setString(
                3,
                "https://example.invalid/"
                    + asin
                    + ".jpg"
            );

            statement.setString(
                4,
                "https://www.amazon.com.br/dp/"
                    + asin
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
        long productId,
        OffsetDateTime collectedAt
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
                collectedAt
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
                4.5
            );

            statement.setLong(
                8,
                800L
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
                "TEST_OPERATIONAL_DETAIL"
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
        long snapshotId,
        boolean eligible,
        RejectionReason rejectionReason,
        BigDecimal score,
        BigDecimal momentum
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
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

            statement.setBoolean(
                2,
                eligible
            );

            statement.setString(
                3,
                rejectionReason == null
                    ? null
                    : rejectionReason.name()
            );

            statement.setString(
                4,
                "TEST_ELIGIBILITY"
            );

            statement.setString(
                5,
                "TEST_FILTER"
            );

            statement.setBigDecimal(
                6,
                score
            );

            statement.setString(
                7,
                score == null
                    ? null
                    : "TEST_SCORE"
            );

            statement.setBigDecimal(
                8,
                momentum
            );

            statement.setString(
                9,
                momentum == null
                    ? null
                    : "TEST_MOMENTUM"
            );

            statement.setObject(
                10,
                BASE_TIME.plusMinutes(
                    snapshotId % 10
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

    private void insertRuleResult(
        Connection connection,
        long evaluationId,
        int ruleOrder,
        String ruleCode,
        boolean passed,
        String observedValue,
        String thresholdValue,
        String reasonCode
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_evaluation_rule_result (
                deal_evaluation_id,
                rule_order,
                rule_code,
                passed,
                observed_value,
                threshold_value,
                reason_code
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            statement.setInt(
                2,
                ruleOrder
            );

            statement.setString(
                3,
                ruleCode
            );

            statement.setBoolean(
                4,
                passed
            );

            statement.setString(
                5,
                observedValue
            );

            statement.setString(
                6,
                thresholdValue
            );

            statement.setString(
                7,
                reasonCode
            );

            statement.executeUpdate();
        }
    }

    private void insertScoreFactor(
        Connection connection,
        long evaluationId,
        int factorOrder,
        String factorCode,
        String status,
        BigDecimal rawValue,
        BigDecimal normalizedValue,
        BigDecimal weight,
        BigDecimal contribution
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_evaluation_score_factor (
                deal_evaluation_id,
                factor_order,
                factor_code,
                status,
                raw_value,
                normalized_value,
                weight,
                contribution
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            statement.setInt(
                2,
                factorOrder
            );

            statement.setString(
                3,
                factorCode
            );

            statement.setString(
                4,
                status
            );

            statement.setBigDecimal(
                5,
                rawValue
            );

            statement.setBigDecimal(
                6,
                normalizedValue
            );

            statement.setBigDecimal(
                7,
                weight
            );

            statement.setBigDecimal(
                8,
                contribution
            );

            statement.executeUpdate();
        }
    }

    private void insertAvailableMomentumAudit(
        Connection connection,
        long evaluationId,
        long previousSnapshotId
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_evaluation_momentum_audit (
                deal_evaluation_id,
                calculation_version,
                status,
                unavailable_reason,
                previous_offer_snapshot_id,
                elapsed_seconds,
                sold_percentage_delta,
                current_price_delta,
                current_price_delta_percentage,
                cash_discount_delta,
                momentum
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            statement.setString(
                2,
                "TEST_MOMENTUM"
            );

            statement.setString(
                3,
                "AVAILABLE"
            );

            statement.setString(
                4,
                null
            );

            statement.setLong(
                5,
                previousSnapshotId
            );

            statement.setLong(
                6,
                3600L
            );

            statement.setBigDecimal(
                7,
                new BigDecimal(
                    "10.0000"
                )
            );

            statement.setBigDecimal(
                8,
                new BigDecimal(
                    "-20.0000"
                )
            );

            statement.setBigDecimal(
                9,
                new BigDecimal(
                    "-16.6800"
                )
            );

            statement.setBigDecimal(
                10,
                null
            );

            statement.setBigDecimal(
                11,
                new BigDecimal(
                    "12.5000"
                )
            );

            statement.executeUpdate();
        }
    }

    private void insertUnavailableMomentumAudit(
        Connection connection,
        long evaluationId
    ) throws Exception {

        String sql =
            """
            INSERT INTO deal_evaluation_momentum_audit (
                deal_evaluation_id,
                calculation_version,
                status,
                unavailable_reason,
                previous_offer_snapshot_id,
                elapsed_seconds,
                sold_percentage_delta,
                current_price_delta,
                current_price_delta_percentage,
                cash_discount_delta,
                momentum
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                evaluationId
            );

            statement.setString(
                2,
                "TEST_MOMENTUM"
            );

            statement.setString(
                3,
                "UNAVAILABLE"
            );

            statement.setString(
                4,
                "NO_PREVIOUS_SNAPSHOT"
            );

            statement.setObject(
                5,
                null
            );

            statement.setObject(
                6,
                null
            );

            statement.setObject(
                7,
                null
            );

            statement.setObject(
                8,
                null
            );

            statement.setObject(
                9,
                null
            );

            statement.setObject(
                10,
                null
            );

            statement.setObject(
                11,
                null
            );

            statement.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface TransactionTest {

        void execute(
            Connection connection
        ) throws Exception;
    }
}
