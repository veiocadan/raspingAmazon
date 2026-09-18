package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.evaluation.DealEvaluationRepository;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Implementação JDBC da persistência de DealEvaluation.
 *
 * <p>Persiste tanto a decisão agregada quanto os resultados
 * individuais das regras.</p>
 */
public final class DealEvaluationJdbcRepository
        implements DealEvaluationRepository {

    private final Connection connection;

    public DealEvaluationJdbcRepository(
            Connection connection
    ) {
        this.connection =
                Objects.requireNonNull(
                        connection,
                        "connection must not be null"
                );
    }

    @Override
    public DealEvaluation save(
            DealEvaluation evaluation
    ) {
        Objects.requireNonNull(
                evaluation,
                "evaluation must not be null"
        );

        if (evaluation.id() != null) {
            throw new IllegalArgumentException(
                    "Only new DealEvaluation instances can be persisted"
            );
        }

        try {

            long evaluationId =
                    insertEvaluation(
                            evaluation
                    );

            /*
             * Cada resultado individual recebe uma linha própria.
             */
            insertRuleResults(
                    evaluationId,
                    evaluation
            );

            return new DealEvaluation(
                    evaluationId,
                    evaluation.offerSnapshot(),
                    evaluation.eligible(),
                    evaluation.rejectionReason(),
                    evaluation.eligibilityPolicyVersion(),
                    evaluation.filterProfileVersion(),
                    evaluation.ruleResults(),
                    evaluation.score(),
                    evaluation.scoreVersion(),
                    evaluation.momentum(),
                    evaluation.momentumVersion(),
                    evaluation.evaluatedAt()
            );

        } catch (SQLException exception) {

            throw new IllegalStateException(
                    "Failed to persist DealEvaluation",
                    exception
            );
        }
    }

    /**
     * Persiste a linha agregada da avaliação.
     */
    private long insertEvaluation(
            DealEvaluation evaluation
    ) throws SQLException {

        String sql = """
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
                    evaluation.offerSnapshot().id()
            );

            statement.setBoolean(
                    2,
                    evaluation.eligible()
            );

            if (evaluation.rejectionReason() == null) {
                statement.setObject(
                        3,
                        null
                );
            } else {
                statement.setString(
                        3,
                        evaluation.rejectionReason().name()
                );
            }

            statement.setString(
                    4,
                    evaluation.eligibilityPolicyVersion()
            );

            setNullableString(
                    statement,
                    5,
                    evaluation.filterProfileVersion()
            );

            if (evaluation.score() == null) {
                statement.setObject(
                        6,
                        null
                );
            } else {
                statement.setBigDecimal(
                        6,
                        evaluation.score()
                );
            }

            setNullableString(
                    statement,
                    7,
                    evaluation.scoreVersion()
            );

            if (evaluation.momentum() == null) {
                statement.setObject(
                        8,
                        null
                );
            } else {
                statement.setBigDecimal(
                        8,
                        evaluation.momentum()
                );
            }

            setNullableString(
                    statement,
                    9,
                    evaluation.momentumVersion()
            );

            statement.setObject(
                    10,
                    evaluation.evaluatedAt()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new SQLException(
                            "Failed to obtain generated deal_evaluation id"
                    );
                }

                return resultSet.getLong(
                        "id"
                );
            }
        }
    }

    /**
     * Persiste todos os resultados de regra mantendo sua ordem.
     */
    private void insertRuleResults(
            long evaluationId,
            DealEvaluation evaluation
    ) throws SQLException {

        String sql = """
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

            for (int index = 0;
                 index < evaluation.ruleResults().size();
                 index++) {

                EvaluationRuleResult result =
                        evaluation.ruleResults().get(
                                index
                        );

                statement.setLong(
                        1,
                        evaluationId
                );

                statement.setInt(
                        2,
                        index
                );

                statement.setString(
                        3,
                        result.ruleCode()
                );

                statement.setBoolean(
                        4,
                        result.passed()
                );

                statement.setString(
                        5,
                        result.observedValue()
                );

                statement.setString(
                        6,
                        result.threshold()
                );

                if (result.reasonCode() == null) {
                    statement.setObject(
                            7,
                            null
                    );
                } else {
                    statement.setString(
                            7,
                            result.reasonCode().name()
                    );
                }

                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private static void setNullableString(
            PreparedStatement statement,
            int index,
            String value
    ) throws SQLException {

        if (value == null) {
            statement.setObject(
                    index,
                    null
            );
        } else {
            statement.setString(
                    index,
                    value
            );
        }
    }
}