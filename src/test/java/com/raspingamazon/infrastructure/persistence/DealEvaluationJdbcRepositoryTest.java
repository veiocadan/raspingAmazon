package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.migration.DatabaseMigration;
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

/**
 * Teste de integração entre DealEvaluation e PostgreSQL.
 *
 * <p>Além da linha agregada em deal_evaluation, a FASE 8.5-C3
 * exige que cada regra aplicada seja persistida individualmente em
 * deal_evaluation_rule_result.</p>
 *
 * <p>Este teste verifica:</p>
 *
 * <ul>
 *     <li>a decisão agregada;</li>
 *     <li>as versões da avaliação;</li>
 *     <li>os resultados individuais das regras;</li>
 *     <li>a ordem das regras;</li>
 *     <li>os motivos individuais de rejeição.</li>
 * </ul>
 */
class DealEvaluationJdbcRepositoryTest {

    @Test
    void shouldPersistEligibleDealEvaluationWithRuleResults()
            throws Exception {

        ApplicationConfig config =
                EnvironmentConfigProvider.load();

        /*
         * Aplica migrations pendentes, inclusive V5.
         */
        DatabaseMigration.migrate(
                config
        );

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

            /*
             * Oferta elegível:
             * ambas as regras passaram.
             */
            List<EvaluationRuleResult> ruleResults =
                    List.of(
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

            DealEvaluation evaluation =
                    new DealEvaluation(
                            null,
                            offerSnapshot,
                            true,
                            null,
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

            /*
             * Verificações no objeto retornado.
             */
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

            assertTrue(
                    persisted.ruleResults()
                            .get(0)
                            .passed()
            );

            assertTrue(
                    persisted.ruleResults()
                            .get(1)
                            .passed()
            );

            assertNull(
                    persisted.score()
            );

            assertNull(
                    persisted.scoreVersion()
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

            /*
             * Verifica diretamente a linha agregada.
             */
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

            /*
             * Verifica diretamente as duas linhas de regra.
             */
            List<PersistedRuleResult> persistedRules =
                    loadRuleResults(
                            connection,
                            persisted.id()
                    );

            assertEquals(
                    2,
                    persistedRules.size()
            );

            PersistedRuleResult sellerRule =
                    persistedRules.get(
                            0
                    );

            assertEquals(
                    0,
                    sellerRule.ruleOrder()
            );

            assertEquals(
                    "SELLER_IS_AMAZON",
                    sellerRule.ruleCode()
            );

            assertTrue(
                    sellerRule.passed()
            );

            assertEquals(
                    "AMAZON",
                    sellerRule.observedValue()
            );

            assertEquals(
                    "AMAZON",
                    sellerRule.thresholdValue()
            );

            assertNull(
                    sellerRule.reasonCode()
            );

            PersistedRuleResult deliveryRule =
                    persistedRules.get(
                            1
                    );

            assertEquals(
                    1,
                    deliveryRule.ruleOrder()
            );

            assertEquals(
                    "DELIVERY_IS_AMAZON",
                    deliveryRule.ruleCode()
            );

            assertTrue(
                    deliveryRule.passed()
            );

            assertEquals(
                    "AMAZON",
                    deliveryRule.observedValue()
            );

            assertEquals(
                    "AMAZON",
                    deliveryRule.thresholdValue()
            );

            assertNull(
                    deliveryRule.reasonCode()
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

        DatabaseMigration.migrate(
                config
        );

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

            /*
             * Neste cenário seller e delivery falham.
             *
             * SELLER continua sendo a rejeição principal,
             * mas DELIVERY também precisa ser persistida.
             */
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
                    2,
                    persisted.ruleResults().size()
            );

            /*
             * Verificamos que a segunda falha não foi perdida.
             */
            assertEquals(
                    RejectionReason.DELIVERY_THIRD_PARTY,
                    persisted.ruleResults()
                            .get(1)
                            .reasonCode()
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

            PersistedRuleResult sellerRule =
                    persistedRules.get(
                            0
                    );

            assertFalse(
                    sellerRule.passed()
            );

            assertEquals(
                    "SELLER_THIRD_PARTY",
                    sellerRule.reasonCode()
            );

            PersistedRuleResult deliveryRule =
                    persistedRules.get(
                            1
                    );

            assertFalse(
                    deliveryRule.passed()
            );

            assertEquals(
                    "DELIVERY_THIRD_PARTY",
                    deliveryRule.reasonCode()
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

    /**
     * Cria um Product persistido e devolve sua representação de domínio.
     */
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

    /**
     * Persiste a linha de offer_snapshot necessária para a foreign key
     * da avaliação.
     */
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

            statement.setObject(
                    7,
                    null
            );

            statement.setObject(
                    8,
                    null
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

    /**
     * Cria a representação de domínio do mesmo snapshot persistido.
     */
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
                null,
                null,
                "Vendedor teste",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "TEST",
                List.of()
        );
    }

    /**
     * Verifica diretamente a linha agregada em deal_evaluation.
     */
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

                assertEquals(
                        score,
                        resultSet.getBigDecimal(
                                "score"
                        )
                );

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

                /*
                 * PostgreSQL pode normalizar precisão de timestamp.
                 */
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

    /**
     * Lê diretamente todos os resultados de regra persistidos.
     *
     * <p>ORDER BY rule_order é importante porque a ordem das regras
     * também faz parte da explicação determinística da avaliação.</p>
     */
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

    /**
     * Remove os dados de teste.
     *
     * <p>A tabela deal_evaluation_rule_result possui ON DELETE CASCADE.
     * Portanto, ao apagar deal_evaluation, seus resultados individuais
     * também são removidos automaticamente.</p>
     */
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

    /**
     * Representação auxiliar de uma linha de
     * deal_evaluation_rule_result lida diretamente do banco.
     */
    private record PersistedRuleResult(
            int ruleOrder,
            String ruleCode,
            boolean passed,
            String observedValue,
            String thresholdValue,
            String reasonCode
    ) {
    }
}