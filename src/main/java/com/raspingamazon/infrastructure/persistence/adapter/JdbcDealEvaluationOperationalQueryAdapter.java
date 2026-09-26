package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.evaluation.DealEvaluationCursor;
import com.raspingamazon.application.operation.evaluation.DealEvaluationPage;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSearchCriteria;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSummary;
import com.raspingamazon.application.operation.evaluation.port.DealEvaluationOperationalQueryPort;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementação JDBC da consulta operacional paginada de
 * DealEvaluation.
 *
 * <p>Este adapter implementa um read model específico e deliberadamente
 * não reconstrói os agregados completos de DealEvaluation,
 * OfferSnapshot e Product.</p>
 *
 * <p>A ordenação é obrigatoriamente:</p>
 *
 * <pre>
 * evaluated_at DESC
 * id DESC
 * </pre>
 *
 * <p>A paginação utiliza keyset pagination e nunca OFFSET.</p>
 */
public final class JdbcDealEvaluationOperationalQueryAdapter
    implements DealEvaluationOperationalQueryPort {

    private final Connection connection;

    public JdbcDealEvaluationOperationalQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public DealEvaluationPage search(
        DealEvaluationSearchCriteria criteria
    ) {

        Objects.requireNonNull(
            criteria,
            "criteria must not be null"
        );

        QueryPlan queryPlan =
            buildQuery(
                criteria
            );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     queryPlan.sql()
                 )) {

            bindParameters(
                statement,
                queryPlan.parameters()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                return readPage(
                    resultSet,
                    criteria.limit()
                );
            }

        } catch (SQLException exception) {

            throw new PersistenceOperationException(
                "Could not search operational deal evaluations",
                exception
            );
        }
    }

    private QueryPlan buildQuery(
        DealEvaluationSearchCriteria criteria
    ) {

        StringBuilder sql =
            new StringBuilder(
                """
                SELECT
                    de.id AS evaluation_id,
                    os.id AS offer_snapshot_id,
                    p.id AS product_id,
                    p.asin,
                    p.title,
                    os.current_price,
                    de.eligible,
                    de.rejection_reason,
                    de.score,
                    de.momentum,
                    os.collected_at,
                    de.evaluated_at
                FROM deal_evaluation de
                INNER JOIN offer_snapshot os
                    ON os.id = de.offer_snapshot_id
                INNER JOIN product p
                    ON p.id = os.product_id
                WHERE 1 = 1
                """
            );

        List<Object> parameters =
            new ArrayList<>();

        appendEligibilityFilter(
            sql,
            parameters,
            criteria
        );

        appendAsinFilter(
            sql,
            parameters,
            criteria
        );

        appendScoreFilters(
            sql,
            parameters,
            criteria
        );

        appendEvaluationPeriodFilters(
            sql,
            parameters,
            criteria
        );

        appendCursorFilter(
            sql,
            parameters,
            criteria
        );

        sql.append(
            """
            ORDER BY
                de.evaluated_at DESC,
                de.id DESC
            LIMIT ?
            """
        );

        /*
         * Buscamos um registro adicional.
         *
         * Esse registro não é devolvido para a aplicação.
         * Ele serve somente para descobrir se existe uma próxima página.
         */
        parameters.add(
            criteria.limit() + 1
        );

        return new QueryPlan(
            sql.toString(),
            parameters
        );
    }

    private void appendEligibilityFilter(
        StringBuilder sql,
        List<Object> parameters,
        DealEvaluationSearchCriteria criteria
    ) {

        if (criteria.eligible() == null) {
            return;
        }

        sql.append(
            """
            AND de.eligible = ?
            """
        );

        parameters.add(
            criteria.eligible()
        );
    }

    private void appendAsinFilter(
        StringBuilder sql,
        List<Object> parameters,
        DealEvaluationSearchCriteria criteria
    ) {

        if (criteria.asin() == null) {
            return;
        }

        sql.append(
            """
            AND p.asin = ?
            """
        );

        parameters.add(
            criteria.asin()
                .value()
        );
    }

    private void appendScoreFilters(
        StringBuilder sql,
        List<Object> parameters,
        DealEvaluationSearchCriteria criteria
    ) {

        if (criteria.minimumScore() != null) {

            sql.append(
                """
                AND de.score >= ?
                """
            );

            parameters.add(
                criteria.minimumScore()
            );
        }

        if (criteria.maximumScore() != null) {

            sql.append(
                """
                AND de.score <= ?
                """
            );

            parameters.add(
                criteria.maximumScore()
            );
        }
    }

    private void appendEvaluationPeriodFilters(
        StringBuilder sql,
        List<Object> parameters,
        DealEvaluationSearchCriteria criteria
    ) {

        if (criteria.evaluatedFrom() != null) {

            sql.append(
                """
                AND de.evaluated_at >= ?
                """
            );

            parameters.add(
                criteria.evaluatedFrom()
            );
        }

        if (criteria.evaluatedUntil() != null) {

            sql.append(
                """
                AND de.evaluated_at <= ?
                """
            );

            parameters.add(
                criteria.evaluatedUntil()
            );
        }
    }

    private void appendCursorFilter(
        StringBuilder sql,
        List<Object> parameters,
        DealEvaluationSearchCriteria criteria
    ) {

        DealEvaluationCursor cursor =
            criteria.after();

        if (cursor == null) {
            return;
        }

        /*
         * PostgreSQL compara ROW values lexicograficamente.
         *
         * Como a ordenação é DESC, os itens posteriores ao cursor
         * possuem a tupla temporal/identidade menor.
         */
        sql.append(
            """
            AND (de.evaluated_at, de.id) < (?, ?)
            """
        );

        parameters.add(
            cursor.evaluatedAt()
        );

        parameters.add(
            cursor.evaluationId()
        );
    }

    private void bindParameters(
        PreparedStatement statement,
        List<Object> parameters
    ) throws SQLException {

        for (int index = 0;
             index < parameters.size();
             index++) {

            Object value =
                parameters.get(
                    index
                );

            int parameterIndex =
                index + 1;

            if (value instanceof Boolean booleanValue) {

                statement.setBoolean(
                    parameterIndex,
                    booleanValue
                );

            } else if (value instanceof String stringValue) {

                statement.setString(
                    parameterIndex,
                    stringValue
                );

            } else if (value instanceof BigDecimal decimalValue) {

                statement.setBigDecimal(
                    parameterIndex,
                    decimalValue
                );

            } else if (value instanceof OffsetDateTime dateTimeValue) {

                statement.setObject(
                    parameterIndex,
                    dateTimeValue
                );

            } else if (value instanceof Long longValue) {

                statement.setLong(
                    parameterIndex,
                    longValue
                );

            } else if (value instanceof Integer integerValue) {

                statement.setInt(
                    parameterIndex,
                    integerValue
                );

            } else {

                throw new IllegalStateException(
                    "Unsupported operational query parameter type: "
                        + value.getClass()
                        .getName()
                );
            }
        }
    }

    private DealEvaluationPage readPage(
        ResultSet resultSet,
        int requestedLimit
    ) throws SQLException {

        List<DealEvaluationSummary> summaries =
            new ArrayList<>(
                requestedLimit + 1
            );

        while (resultSet.next()) {

            summaries.add(
                readSummary(
                    resultSet
                )
            );
        }

        boolean hasMore =
            summaries.size()
                > requestedLimit;

        if (hasMore) {

            summaries.remove(
                summaries.size() - 1
            );
        }

        DealEvaluationCursor nextCursor =
            null;

        if (hasMore) {

            DealEvaluationSummary lastItem =
                summaries.get(
                    summaries.size() - 1
                );

            nextCursor =
                new DealEvaluationCursor(
                    lastItem.evaluatedAt(),
                    lastItem.evaluationId()
                );
        }

        return new DealEvaluationPage(
            summaries,
            nextCursor
        );
    }

    private DealEvaluationSummary readSummary(
        ResultSet resultSet
    ) throws SQLException {

        return new DealEvaluationSummary(
            resultSet.getLong(
                "evaluation_id"
            ),
            resultSet.getLong(
                "offer_snapshot_id"
            ),
            resultSet.getLong(
                "product_id"
            ),
            new Asin(
                resultSet.getString(
                    "asin"
                )
            ),
            resultSet.getString(
                "title"
            ),
            new Money(
                resultSet.getBigDecimal(
                    "current_price"
                )
            ),
            resultSet.getBoolean(
                "eligible"
            ),
            readRejectionReason(
                resultSet
            ),
            resultSet.getBigDecimal(
                "score"
            ),
            resultSet.getBigDecimal(
                "momentum"
            ),
            resultSet.getObject(
                "collected_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "evaluated_at",
                OffsetDateTime.class
            )
        );
    }

    private RejectionReason readRejectionReason(
        ResultSet resultSet
    ) throws SQLException {

        String persistedValue =
            resultSet.getString(
                "rejection_reason"
            );

        if (persistedValue == null) {
            return null;
        }

        try {

            return RejectionReason.valueOf(
                persistedValue
            );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                "Unknown persisted rejection reason: "
                    + persistedValue,
                exception
            );
        }
    }

    private record QueryPlan(
        String sql,
        List<Object> parameters
    ) {

        private QueryPlan {

            Objects.requireNonNull(
                sql,
                "QueryPlan sql must not be null"
            );

            Objects.requireNonNull(
                parameters,
                "QueryPlan parameters must not be null"
            );

            parameters =
                List.copyOf(
                    parameters
                );
        }
    }
}
