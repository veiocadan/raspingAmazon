package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.scoring.ScoreFactorCode;
import com.raspingamazon.domain.scoring.ScoreFactorResult;
import com.raspingamazon.domain.scoring.ScoreFactorStatus;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class DealEvaluationJdbcRepositoryTest {

    @Test
    void shouldPersistEligibleDealEvaluationWithRuleResults()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();


        long productId = 0;
        long offerSnapshotId = 0;
        long evaluationId = 0;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            Product product =
                createProduct(
                    connection,
                    "B000TEST86"
                );

            productId =
                product.id();

            offerSnapshotId =
                createOfferSnapshot(
                    connection,
                    product.id()
                );

            OfferSnapshot offerSnapshot =
                createOfferSnapshotDomain(
                    product,
                    offerSnapshotId
                );

            OffsetDateTime evaluatedAt =
                OffsetDateTime.now();

            DealEvaluation evaluation =
                new DealEvaluation(
                    null,
                    offerSnapshot,
                    true,
                    null,
                    "AMAZON_SELLER_DELIVERY_V1",
                    null,
                    passedRuleResults(),
                    null,
                    null,
                    null,
                    null,
                    evaluatedAt
                );

            DealEvaluationJdbcRepository repository =
                new DealEvaluationJdbcRepository(
                    connection
                );

            DealEvaluation persisted =
                repository.save(
                    evaluation
                );

            evaluationId =
                persisted.id();

            assertNotNull(
                persisted
            );

            assertNotNull(
                persisted.id()
            );

            assertTrue(
                persisted.id() > 0
            );

            assertEquals(
                offerSnapshotId,
                persisted.offerSnapshot().id()
            );

            assertTrue(
                persisted.eligible()
            );

            assertNull(
                persisted.rejectionReason()
            );

            assertEquals(
                "AMAZON_SELLER_DELIVERY_V1",
                persisted.eligibilityPolicyVersion()
            );

            assertNull(
                persisted.filterProfileVersion()
            );

            assertEquals(
                2,
                persisted.ruleResults().size()
            );

            assertNull(
                persisted.score()
            );

            assertNull(
                persisted.scoreVersion()
            );

            assertTrue(
                persisted.scoreFactors().isEmpty()
            );

            assertNull(
                persisted.momentum()
            );

            assertNull(
                persisted.momentumVersion()
            );

            assertEquals(
                evaluatedAt.toInstant(),
                persisted.evaluatedAt().toInstant()
            );

            assertDatabaseEvaluationRow(
                connection,
                persisted.id(),
                offerSnapshotId,
                true,
                null,
                "AMAZON_SELLER_DELIVERY_V1",
                null,
                null,
                null,
                null,
                null,
                evaluatedAt
            );

            List<PersistedRuleResult> persistedRules =
                loadRuleResults(
                    connection,
                    persisted.id()
                );

            assertEquals(
                2,
                persistedRules.size()
            );

            assertEquals(
                0,
                persistedRules.get(0).ruleOrder()
            );

            assertEquals(
                "SELLER_IS_AMAZON",
                persistedRules.get(0).ruleCode()
            );

            assertTrue(
                persistedRules.get(0).passed()
            );

            assertEquals(
                1,
                persistedRules.get(1).ruleOrder()
            );

            assertEquals(
                "DELIVERY_IS_AMAZON",
                persistedRules.get(1).ruleCode()
            );

            assertTrue(
                persistedRules.get(1).passed()
            );

            assertTrue(
                loadScoreFactors(
                    connection,
                    persisted.id()
                ).isEmpty()
            );

        } finally {

            cleanup(
                config,
                evaluationId,
                offerSnapshotId,
                productId
            );
        }
    }

    @Test
    void shouldPersistRejectedDealEvaluationWithMultipleFailures()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();


        long productId = 0;
        long offerSnapshotId = 0;
        long evaluationId = 0;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            Product product =
                createProduct(
                    connection,
                    "B000TEST87"
                );

            productId =
                product.id();

            offerSnapshotId =
                createOfferSnapshot(
                    connection,
                    product.id()
                );

            OfferSnapshot offerSnapshot =
                createOfferSnapshotDomain(
                    product,
                    offerSnapshotId
                );

            OffsetDateTime evaluatedAt =
                OffsetDateTime.now();

            List<EvaluationRuleResult> ruleResults =
                List.of(
                    EvaluationRuleResult.failed(
                        "SELLER_IS_AMAZON",
                        "THIRD_PARTY",
                        "AMAZON",
                        RejectionReason.SELLER_THIRD_PARTY
                    ),
                    EvaluationRuleResult.failed(
                        "DELIVERY_IS_AMAZON",
                        "THIRD_PARTY",
                        "AMAZON",
                        RejectionReason.DELIVERY_THIRD_PARTY
                    )
                );

            DealEvaluation evaluation =
                new DealEvaluation(
                    null,
                    offerSnapshot,
                    false,
                    RejectionReason.SELLER_THIRD_PARTY,
                    "AMAZON_SELLER_DELIVERY_V1",
                    null,
                    ruleResults,
                    null,
                    null,
                    null,
                    null,
                    evaluatedAt
                );

            DealEvaluationJdbcRepository repository =
                new DealEvaluationJdbcRepository(
                    connection
                );

            DealEvaluation persisted =
                repository.save(
                    evaluation
                );

            evaluationId =
                persisted.id();

            assertNotNull(
                persisted.id()
            );

            assertFalse(
                persisted.eligible()
            );

            assertEquals(
                RejectionReason.SELLER_THIRD_PARTY,
                persisted.rejectionReason()
            );

            assertEquals(
                RejectionReason.DELIVERY_THIRD_PARTY,
                persisted.ruleResults()
                    .get(1)
                    .reasonCode()
            );

            assertNull(
                persisted.score()
            );

            assertTrue(
                persisted.scoreFactors().isEmpty()
            );

            assertDatabaseEvaluationRow(
                connection,
                persisted.id(),
                offerSnapshotId,
                false,
                RejectionReason.SELLER_THIRD_PARTY.name(),
                "AMAZON_SELLER_DELIVERY_V1",
                null,
                null,
                null,
                null,
                null,
                evaluatedAt
            );

            List<PersistedRuleResult> persistedRules =
                loadRuleResults(
                    connection,
                    persisted.id()
                );

            assertEquals(
                2,
                persistedRules.size()
            );

            assertFalse(
                persistedRules.get(0).passed()
            );

            assertEquals(
                "SELLER_THIRD_PARTY",
                persistedRules.get(0).reasonCode()
            );

            assertFalse(
                persistedRules.get(1).passed()
            );

            assertEquals(
                "DELIVERY_THIRD_PARTY",
                persistedRules.get(1).reasonCode()
            );

            assertTrue(
                loadScoreFactors(
                    connection,
                    persisted.id()
                ).isEmpty()
            );

        } finally {

            cleanup(
                config,
                evaluationId,
                offerSnapshotId,
                productId
            );
        }
    }

    @Test
    void shouldPersistScoreAndScoreFactors()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();


        long productId = 0;
        long offerSnapshotId = 0;
        long evaluationId = 0;

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            Product product =
                createProduct(
                    connection,
                    "B000TEST88"
                );

            productId =
                product.id();

            offerSnapshotId =
                createOfferSnapshot(
                    connection,
                    product.id()
                );

            OfferSnapshot offerSnapshot =
                createOfferSnapshotDomain(
                    product,
                    offerSnapshotId
                );

            OffsetDateTime evaluatedAt =
                OffsetDateTime.now();

            List<ScoreFactorResult> scoreFactors =
                List.of(
                    ScoreFactorResult.unavailable(
                        ScoreFactorCode.SOLD_PERCENTAGE,
                        new BigDecimal("30")
                    ),
                    ScoreFactorResult.available(
                        ScoreFactorCode.CASH_DISCOUNT,
                        new BigDecimal("20"),
                        new BigDecimal("20"),
                        new BigDecimal("25"),
                        new BigDecimal("5")
                    ),
                    ScoreFactorResult.available(
                        ScoreFactorCode.RATING,
                        new BigDecimal("4.5"),
                        new BigDecimal("90"),
                        new BigDecimal("20"),
                        new BigDecimal("18")
                    ),
                    ScoreFactorResult.available(
                        ScoreFactorCode.REVIEW_COUNT,
                        new BigDecimal("500"),
                        new BigDecimal("50"),
                        new BigDecimal("15"),
                        new BigDecimal("7.5")
                    )
                );

            DealEvaluation evaluation =
                new DealEvaluation(
                    null,
                    offerSnapshot,
                    true,
                    null,
                    "AMAZON_SELLER_DELIVERY_V1",
                    "COMMERCIAL_FILTER_V1",
                    passedRuleResults(),
                    new BigDecimal("30.5000"),
                    "SCORE_V1",
                    scoreFactors,
                    null,
                    null,
                    evaluatedAt
                );

            DealEvaluationJdbcRepository repository =
                new DealEvaluationJdbcRepository(
                    connection
                );

            DealEvaluation persisted =
                repository.save(
                    evaluation
                );

            evaluationId =
                persisted.id();

            assertNotNull(
                persisted.id()
            );

            assertTrue(
                persisted.eligible()
            );

            assertBigDecimalEquals(
                "30.5000",
                persisted.score()
            );

            assertEquals(
                "SCORE_V1",
                persisted.scoreVersion()
            );

            assertEquals(
                4,
                persisted.scoreFactors().size()
            );

            assertDatabaseEvaluationRow(
                connection,
                persisted.id(),
                offerSnapshotId,
                true,
                null,
                "AMAZON_SELLER_DELIVERY_V1",
                "COMMERCIAL_FILTER_V1",
                new BigDecimal("30.5000"),
                "SCORE_V1",
                null,
                null,
                evaluatedAt
            );

            List<PersistedScoreFactor> persistedFactors =
                loadScoreFactors(
                    connection,
                    persisted.id()
                );

            assertEquals(
                4,
                persistedFactors.size()
            );

            PersistedScoreFactor soldPercentage =
                persistedFactors.get(
                    0
                );

            assertEquals(
                0,
                soldPercentage.factorOrder()
            );

            assertEquals(
                "SOLD_PERCENTAGE",
                soldPercentage.factorCode()
            );

            assertEquals(
                "UNAVAILABLE",
                soldPercentage.status()
            );

            assertNull(
                soldPercentage.rawValue()
            );

            assertNull(
                soldPercentage.normalizedValue()
            );

            assertBigDecimalEquals(
                "30.0000",
                soldPercentage.weight()
            );

            assertBigDecimalEquals(
                "0.0000",
                soldPercentage.contribution()
            );

            PersistedScoreFactor cashDiscount =
                persistedFactors.get(
                    1
                );

            assertEquals(
                "CASH_DISCOUNT",
                cashDiscount.factorCode()
            );

            assertEquals(
                ScoreFactorStatus.AVAILABLE.name(),
                cashDiscount.status()
            );

            assertBigDecimalEquals(
                "20",
                cashDiscount.rawValue()
            );

            assertBigDecimalEquals(
                "20.0000",
                cashDiscount.normalizedValue()
            );

            assertBigDecimalEquals(
                "25.0000",
                cashDiscount.weight()
            );

            assertBigDecimalEquals(
                "5.0000",
                cashDiscount.contribution()
            );

            PersistedScoreFactor rating =
                persistedFactors.get(
                    2
                );

            assertEquals(
                "RATING",
                rating.factorCode()
            );

            assertBigDecimalEquals(
                "4.5",
                rating.rawValue()
            );

            assertBigDecimalEquals(
                "90.0000",
                rating.normalizedValue()
            );

            assertBigDecimalEquals(
                "18.0000",
                rating.contribution()
            );

            PersistedScoreFactor reviewCount =
                persistedFactors.get(
                    3
                );

            assertEquals(
                "REVIEW_COUNT",
                reviewCount.factorCode()
            );

            assertBigDecimalEquals(
                "500",
                reviewCount.rawValue()
            );

            assertBigDecimalEquals(
                "50.0000",
                reviewCount.normalizedValue()
            );

            assertBigDecimalEquals(
                "15.0000",
                reviewCount.weight()
            );

            assertBigDecimalEquals(
                "7.5000",
                reviewCount.contribution()
            );

        } finally {

            cleanup(
                config,
                evaluationId,
                offerSnapshotId,
                productId
            );
        }
    }

    private static List<EvaluationRuleResult> passedRuleResults() {

        return List.of(
            EvaluationRuleResult.passed(
                "SELLER_IS_AMAZON",
                "AMAZON",
                "AMAZON"
            ),
            EvaluationRuleResult.passed(
                "DELIVERY_IS_AMAZON",
                "AMAZON",
                "AMAZON"
            )
        );
    }

    private Product createProduct(
        Connection connection,
        String asin
    ) throws SQLException {

        ProductRepository repository =
            new ProductRepository(
                connection
            );

        long productId =
            repository.insert(
                asin,
                "Produto de teste",
                null,
                "https://example.invalid/produto/"
                    + asin
            );

        return new Product(
            productId,
            new Asin(
                asin
            ),
            "Produto de teste",
            null,
            "https://example.invalid/produto/"
                + asin
        );
    }

    private long createOfferSnapshot(
        Connection connection,
        long productId
    ) throws SQLException {

        String sql = """
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
                OffsetDateTime.now()
            );

            statement.setBigDecimal(
                3,
                new BigDecimal("99.90")
            );

            statement.setBigDecimal(
                4,
                new BigDecimal("129.90")
            );

            statement.setBigDecimal(
                5,
                new BigDecimal("119.90")
            );

            statement.setObject(
                6,
                null
            );

            statement.setBigDecimal(
                7,
                new BigDecimal("4.5")
            );

            statement.setLong(
                8,
                500
            );

            statement.setString(
                9,
                "Vendedor teste"
            );

            statement.setString(
                10,
                "Amazon"
            );

            statement.setString(
                11,
                "TEST"
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new SQLException(
                        "Failed to obtain generated offer_snapshot id"
                    );
                }

                return resultSet.getLong(
                    "id"
                );
            }
        }
    }

    private OfferSnapshot createOfferSnapshotDomain(
        Product product,
        long offerSnapshotId
    ) {

        return new OfferSnapshot(
            offerSnapshotId,
            product,
            OffsetDateTime.now(),
            new Money(
                new BigDecimal("99.90")
            ),
            new Money(
                new BigDecimal("129.90")
            ),
            new Money(
                new BigDecimal("119.90")
            ),
            null,

            /*
             * OfferSnapshot usa Double para rating.
             *
             * O BigDecimal é utilizado no domínio de scoring,
             * mas a entidade OfferSnapshot ainda possui o contrato
             * histórico Double.
             */
            4.5,

            500L,
            "Vendedor teste",
            "Amazon",
            SellerType.AMAZON,
            DeliveryType.AMAZON,
            "TEST",
            List.of()
        );
    }

    private void assertDatabaseEvaluationRow(
        Connection connection,
        long evaluationId,
        long offerSnapshotId,
        boolean eligible,
        String rejectionReason,
        String eligibilityPolicyVersion,
        String filterProfileVersion,
        BigDecimal score,
        String scoreVersion,
        BigDecimal momentum,
        String momentumVersion,
        OffsetDateTime evaluatedAt
    ) throws SQLException {

        String sql = """
                SELECT
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
                FROM deal_evaluation
                WHERE id = ?
                """;

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

                assertTrue(
                    resultSet.next(),
                    "Persisted deal_evaluation row was not found"
                );

                assertEquals(
                    offerSnapshotId,
                    resultSet.getLong(
                        "offer_snapshot_id"
                    )
                );

                assertEquals(
                    eligible,
                    resultSet.getBoolean(
                        "eligible"
                    )
                );

                assertEquals(
                    rejectionReason,
                    resultSet.getString(
                        "rejection_reason"
                    )
                );

                assertEquals(
                    eligibilityPolicyVersion,
                    resultSet.getString(
                        "eligibility_policy_version"
                    )
                );

                assertEquals(
                    filterProfileVersion,
                    resultSet.getString(
                        "filter_profile_version"
                    )
                );

                if (score == null) {

                    assertNull(
                        resultSet.getBigDecimal(
                            "score"
                        )
                    );

                } else {

                    assertBigDecimalEquals(
                        score.toPlainString(),
                        resultSet.getBigDecimal(
                            "score"
                        )
                    );
                }

                assertEquals(
                    scoreVersion,
                    resultSet.getString(
                        "score_version"
                    )
                );

                assertEquals(
                    momentum,
                    resultSet.getBigDecimal(
                        "momentum"
                    )
                );

                assertEquals(
                    momentumVersion,
                    resultSet.getString(
                        "momentum_version"
                    )
                );

                OffsetDateTime databaseEvaluatedAt =
                    resultSet.getObject(
                        "evaluated_at",
                        OffsetDateTime.class
                    );

                Duration difference =
                    Duration.between(
                        evaluatedAt.toInstant(),
                        databaseEvaluatedAt.toInstant()
                    ).abs();

                assertTrue(
                    difference.compareTo(
                        Duration.ofNanos(
                            1_000
                        )
                    ) <= 0,
                    "Database timestamp differs from application timestamp by more than 1 microsecond"
                );
            }
        }
    }

    private List<PersistedRuleResult> loadRuleResults(
        Connection connection,
        long evaluationId
    ) throws SQLException {

        String sql = """
                SELECT
                    rule_order,
                    rule_code,
                    passed,
                    observed_value,
                    threshold_value,
                    reason_code
                FROM deal_evaluation_rule_result
                WHERE deal_evaluation_id = ?
                ORDER BY rule_order
                """;

        List<PersistedRuleResult> results =
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

                    results.add(
                        new PersistedRuleResult(
                            resultSet.getInt(
                                "rule_order"
                            ),
                            resultSet.getString(
                                "rule_code"
                            ),
                            resultSet.getBoolean(
                                "passed"
                            ),
                            resultSet.getString(
                                "observed_value"
                            ),
                            resultSet.getString(
                                "threshold_value"
                            ),
                            resultSet.getString(
                                "reason_code"
                            )
                        )
                    );
                }
            }
        }

        return results;
    }

    private List<PersistedScoreFactor> loadScoreFactors(
        Connection connection,
        long evaluationId
    ) throws SQLException {

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

        List<PersistedScoreFactor> results =
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

                    results.add(
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

        return results;
    }

    private void cleanup(
        ApplicationConfig config,
        long evaluationId,
        long offerSnapshotId,
        long productId
    ) {

        if (evaluationId == 0
            && offerSnapshotId == 0
            && productId == 0) {
            return;
        }

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            if (evaluationId > 0) {

                try (PreparedStatement statement =
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
                }
            }

            if (offerSnapshotId > 0) {

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM offer_snapshot
                             WHERE id = ?
                             """
                         )) {

                    statement.setLong(
                        1,
                        offerSnapshotId
                    );

                    statement.executeUpdate();
                }
            }

            if (productId > 0) {

                try (PreparedStatement statement =
                         connection.prepareStatement(
                             """
                             DELETE FROM product
                             WHERE id = ?
                             """
                         )) {

                    statement.setLong(
                        1,
                        productId
                    );

                    statement.executeUpdate();
                }
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to clean up persistence test data",
                exception
            );
        }
    }

    private static void assertBigDecimalEquals(
        String expected,
        BigDecimal actual
    ) {

        assertNotNull(
            actual
        );

        assertEquals(
            0,
            new BigDecimal(expected).compareTo(actual)
        );
    }

    private record PersistedRuleResult(
        int ruleOrder,
        String ruleCode,
        boolean passed,
        String observedValue,
        String thresholdValue,
        String reasonCode
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
}
