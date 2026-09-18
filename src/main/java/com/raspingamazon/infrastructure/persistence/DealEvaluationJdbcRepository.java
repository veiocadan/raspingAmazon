package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.evaluation.DealEvaluationRepository;
import com.raspingamazon.domain.evaluation.DealEvaluation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Implementação JDBC da persistência de DealEvaluation.
 *
 * <p>As versões dos diferentes estágios de decisão são persistidas
 * separadamente para preservar a semântica histórica.</p>
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
                        evaluation
                                .rejectionReason()
                                .name()
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
                    throw new IllegalStateException(
                            "Failed to obtain generated deal_evaluation id"
                    );
                }

                long id =
                        resultSet.getLong(
                                "id"
                        );

                return new DealEvaluation(
                        id,
                        evaluation.offerSnapshot(),
                        evaluation.eligible(),
                        evaluation.rejectionReason(),
                        evaluation.eligibilityPolicyVersion(),
                        evaluation.filterProfileVersion(),
                        evaluation.score(),
                        evaluation.scoreVersion(),
                        evaluation.momentum(),
                        evaluation.momentumVersion(),
                        evaluation.evaluatedAt()
                );
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                    "Failed to persist DealEvaluation",
                    exception
            );
        }
    }

    /**
     * Centraliza o tratamento JDBC de String opcional.
     */
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