package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.PublicationData;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.scoring.ScoreFactorCode;
import com.raspingamazon.domain.scoring.ScoreFactorStatus;
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
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPublicationDataQueryAdapterTest {

    private static final String ASIN =
        "B0PUB13001";

    private static final OffsetDateTime COLLECTED_AT =
        OffsetDateTime.parse(
            "2026-09-23T14:00:00-03:00"
        );

    private static final OffsetDateTime EVALUATED_AT =
        OffsetDateTime.parse(
            "2026-09-23T14:05:00-03:00"
        );

    @Test
    void shouldReconstructPublicationDataFromPersistedEvaluation()
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
                createCompleteEvaluation(
                    connection
                );

            JdbcOfferSnapshotEvaluationLoadAdapter
                snapshotLoadAdapter =
                new JdbcOfferSnapshotEvaluationLoadAdapter(
                    connection
                );

            JdbcPublicationDataQueryAdapter adapter =
                new JdbcPublicationDataQueryAdapter(
                    connection,
                    snapshotLoadAdapter
                );

            PublicationData publicationData =
                adapter.findByDealEvaluationId(
                        data.evaluationId()
                    )
                    .orElseThrow();

            DealEvaluation evaluation =
                publicationData.dealEvaluation();

            assertEquals(
                data.evaluationId(),
                evaluation.id()
            );

            assertEquals(
                data.snapshotId(),
                publicationData.offerSnapshotId()
            );

            assertEquals(
                data.productId(),
                publicationData.productId()
            );

            assertEquals(
                ASIN,
                publicationData.product()
                    .asin()
                    .value()
            );

            assertEquals(
                new BigDecimal(
                    "99.90"
                ),
                publicationData.offerSnapshot()
                    .currentPrice()
                    .amount()
            );

            assertEquals(
                SellerType.AMAZON,
                publicationData.offerSnapshot()
                    .sellerType()
            );

            assertEquals(
                DeliveryType.AMAZON,
                publicationData.offerSnapshot()
                    .deliveryType()
            );

            assertTrue(
                evaluation.eligible()
            );

            assertEquals(
                "AMAZON_ELIGIBILITY_TEST",
                evaluation.eligibilityPolicyVersion()
            );

            assertEquals(
                "COMMERCIAL_FILTER_TEST",
                evaluation.filterProfileVersion()
            );

            assertEquals(
                new BigDecimal(
                    "18.0000"
                ),
                evaluation.score()
            );

            assertEquals(
                "SCORE_TEST",
                evaluation.scoreVersion()
            );

            assertEquals(
                new BigDecimal(
                    "12.5000"
                ),
                evaluation.momentum()
            );

            assertEquals(
                "MOMENTUM_TEST",
                evaluation.momentumVersion()
            );

            /*
             * TIMESTAMPTZ preserva o instante temporal, não a
             * representação textual original do offset.
             *
             * PostgreSQL/JDBC pode reconstruir:
             *
             * 2026-09-23T14:05-03:00
             *
             * como:
             *
             * 2026-09-23T17:05Z
             *
             * Ambas as representações correspondem exatamente
             * ao mesmo instante.
             */
            assertEquals(
                EVALUATED_AT.toInstant(),
                evaluation.evaluatedAt()
                    .toInstant()
            );

            assertEquals(
                1,
                evaluation.ruleResults()
                    .size()
            );

            assertEquals(
                "SELLER_IS_AMAZON",
                evaluation.ruleResults()
                    .getFirst()
                    .ruleCode()
            );

            assertTrue(
                evaluation.ruleResults()
                    .getFirst()
                    .passed()
            );

            assertEquals(
                1,
                evaluation.scoreFactors()
                    .size()
            );

            assertEquals(
                ScoreFactorCode.RATING,
                evaluation.scoreFactors()
                    .getFirst()
                    .code()
            );

            assertEquals(
                ScoreFactorStatus.AVAILABLE,
                evaluation.scoreFactors()
                    .getFirst()
                    .status()
            );

            assertEquals(
                new BigDecimal(
                    "4.5000"
                ),
                evaluation.scoreFactors()
                    .getFirst()
                    .rawValue()
            );

            assertEquals(
                new BigDecimal(
                    "90.0000"
                ),
                evaluation.scoreFactors()
                    .getFirst()
                    .normalizedValue()
            );

            assertEquals(
                new BigDecimal(
                    "20.0000"
                ),
                evaluation.scoreFactors()
                    .getFirst()
                    .weight()
            );

            assertEquals(
                new BigDecimal(
                    "18.0000"
                ),
                evaluation.scoreFactors()
                    .getFirst()
                    .contribution()
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

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcOfferSnapshotEvaluationLoadAdapter
                snapshotLoadAdapter =
                new JdbcOfferSnapshotEvaluationLoadAdapter(
                    connection
                );

            JdbcPublicationDataQueryAdapter adapter =
                new JdbcPublicationDataQueryAdapter(
                    connection,
                    snapshotLoadAdapter
                );

            Optional<PublicationData> result =
                adapter.findByDealEvaluationId(
                    Long.MAX_VALUE
                );

            assertTrue(
                result.isEmpty()
            );
        }
    }

    @Test
    void shouldRejectNonPositiveEvaluationId()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcOfferSnapshotEvaluationLoadAdapter
                snapshotLoadAdapter =
                new JdbcOfferSnapshotEvaluationLoadAdapter(
                    connection
                );

            JdbcPublicationDataQueryAdapter adapter =
                new JdbcPublicationDataQueryAdapter(
                    connection,
                    snapshotLoadAdapter
                );

            assertThrows(
                IllegalArgumentException.class,
                () -> adapter.findByDealEvaluationId(
                    0L
                )
            );

            assertThrows(
                IllegalArgumentException.class,
                () -> adapter.findByDealEvaluationId(
                    -1L
                )
            );
        }
    }

    private TestData createCompleteEvaluation(
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
            SellerType.AMAZON.name()
        );

        insertEvidence(
            connection,
            snapshotId,
            "DELIVERY",
            DeliveryType.AMAZON.name()
        );

        long evaluationId =
            insertEvaluation(
                connection,
                snapshotId
            );

        insertRuleResult(
            connection,
            evaluationId
        );

        insertScoreFactor(
            connection,
            evaluationId
        );

        return new TestData(
            productId,
            snapshotId,
            evaluationId
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
                "Produto publicação"
            );

            statement.setString(
                3,
                "https://example.invalid/publication.jpg"
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
                "TEST_PUBLICATION"
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
        String normalizedValue
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
                normalizedValue
            );

            statement.setString(
                4,
                normalizedValue
            );

            statement.setString(
                5,
                "publication-test"
            );

            statement.setString(
                6,
                "publication-test"
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
                true
            );

            statement.setObject(
                3,
                null
            );

            statement.setString(
                4,
                "AMAZON_ELIGIBILITY_TEST"
            );

            statement.setString(
                5,
                "COMMERCIAL_FILTER_TEST"
            );

            statement.setBigDecimal(
                6,
                new BigDecimal(
                    "18.0000"
                )
            );

            statement.setString(
                7,
                "SCORE_TEST"
            );

            statement.setBigDecimal(
                8,
                new BigDecimal(
                    "12.5000"
                )
            );

            statement.setString(
                9,
                "MOMENTUM_TEST"
            );

            statement.setObject(
                10,
                EVALUATED_AT
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
        long evaluationId
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
                0
            );

            statement.setString(
                3,
                "SELLER_IS_AMAZON"
            );

            statement.setBoolean(
                4,
                true
            );

            statement.setString(
                5,
                "AMAZON"
            );

            statement.setString(
                6,
                "AMAZON"
            );

            statement.setObject(
                7,
                null
            );

            statement.executeUpdate();
        }
    }

    private void insertScoreFactor(
        Connection connection,
        long evaluationId
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
                0
            );

            statement.setString(
                3,
                ScoreFactorCode.RATING.name()
            );

            statement.setString(
                4,
                ScoreFactorStatus.AVAILABLE.name()
            );

            statement.setBigDecimal(
                5,
                new BigDecimal(
                    "4.5000"
                )
            );

            statement.setBigDecimal(
                6,
                new BigDecimal(
                    "90.0000"
                )
            );

            statement.setBigDecimal(
                7,
                new BigDecimal(
                    "20.0000"
                )
            );

            statement.setBigDecimal(
                8,
                new BigDecimal(
                    "18.0000"
                )
            );

            statement.executeUpdate();
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

            deleteById(
                connection,
                """
                DELETE FROM deal_evaluation
                WHERE id = ?
                """,
                data.evaluationId()
            );

            deleteById(
                connection,
                """
                DELETE FROM offer_evidence
                WHERE offer_snapshot_id = ?
                """,
                data.snapshotId()
            );

            deleteById(
                connection,
                """
                DELETE FROM offer_snapshot
                WHERE id = ?
                """,
                data.snapshotId()
            );

            deleteById(
                connection,
                """
                DELETE FROM product
                WHERE id = ?
                """,
                data.productId()
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                "Could not clean publication query test data",
                exception
            );
        }
    }

    private void deleteById(
        Connection connection,
        String sql,
        long id
    ) throws Exception {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                id
            );

            statement.executeUpdate();
        }
    }

    private record TestData(
        long productId,
        long snapshotId,
        long evaluationId
    ) {
    }
}
