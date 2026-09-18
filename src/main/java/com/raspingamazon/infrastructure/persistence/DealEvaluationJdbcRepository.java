package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.application.evaluation.DealEvaluationRepository;
import com.raspingamazon.domain.evaluation.DealEvaluation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Implementação JDBC do contrato de persistência de DealEvaluation.
 *
 * <p>Este componente somente persiste o resultado produzido pela
 * camada de aplicação. Não implementa regras de elegibilidade,
 * filtros, score ou momentum.</p>
 */
public final class DealEvaluationJdbcRepository
        implements DealEvaluationRepository {

    private final Connection connection;

    public DealEvaluationJdbcRepository(
            Connection connection
    ) {
        this.connection = Objects.requireNonNull(
                connection,
                "connection must not be null"
        );
    }

    /**
     * Persiste uma avaliação e retorna a entidade com o identificador
     * gerado pelo banco.
     *
     * @param evaluation avaliação produzida pela aplicação
     * @return avaliação com o identificador persistido
     */
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
                    filter_version,
                    score,
                    momentum,
                    evaluated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

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
                    evaluation.filterVersion()
            );

            if (evaluation.score() == null) {
                statement.setObject(
                        5,
                        null
                );
            } else {
                statement.setBigDecimal(
                        5,
                        evaluation.score()
                );
            }

            if (evaluation.momentum() == null) {
                statement.setObject(
                        6,
                        null
                );
            } else {
                statement.setBigDecimal(
                        6,
                        evaluation.momentum()
                );
            }

            statement.setObject(
                    7,
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
                        resultSet.getLong("id");

                return new DealEvaluation(
                        id,
                        evaluation.offerSnapshot(),
                        evaluation.eligible(),
                        evaluation.rejectionReason(),
                        evaluation.filterVersion(),
                        evaluation.score(),
                        evaluation.momentum(),
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
}