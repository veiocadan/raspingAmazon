package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.publication.PublicationCursor;
import com.raspingamazon.application.operation.publication.PublicationPage;
import com.raspingamazon.application.operation.publication.PublicationSearchCriteria;
import com.raspingamazon.application.operation.publication.PublicationSummary;
import com.raspingamazon.application.operation.publication.port.PublicationOperationalQueryPort;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Consulta JDBC paginada de Publication para uso operacional.
 *
 * <p>Somente as informações necessárias para a listagem são
 * carregadas. generated_text e affiliate_url pertencem à consulta
 * operacional de detalhe.</p>
 */
public final class JdbcPublicationOperationalQueryAdapter
    implements PublicationOperationalQueryPort {

    private final Connection connection;

    public JdbcPublicationOperationalQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public PublicationPage search(
        PublicationSearchCriteria criteria
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
                "Could not search operational Publication records",
                exception
            );
        }
    }

    private QueryPlan buildQuery(
        PublicationSearchCriteria criteria
    ) {

        StringBuilder sql =
            new StringBuilder(
                """
                SELECT
                    pub.id AS publication_id,
                    pub.deal_evaluation_id,
                    p.id AS product_id,
                    p.asin,
                    p.title,
                    pub.status,
                    pub.template_version,
                    pub.commercial_presentation_version,
                    pub.affiliate_link_version,
                    pub.created_at
                FROM publication pub
                INNER JOIN deal_evaluation de
                    ON de.id = pub.deal_evaluation_id
                INNER JOIN offer_snapshot os
                    ON os.id = de.offer_snapshot_id
                INNER JOIN product p
                    ON p.id = os.product_id
                WHERE 1 = 1
                """
            );

        List<Object> parameters =
            new ArrayList<>();

        appendStatusFilter(
            sql,
            parameters,
            criteria
        );

        appendAsinFilter(
            sql,
            parameters,
            criteria
        );

        appendEvaluationFilter(
            sql,
            parameters,
            criteria
        );

        appendCreationPeriodFilters(
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
                pub.created_at DESC,
                pub.id DESC
            LIMIT ?
            """
        );

        parameters.add(
            criteria.limit() + 1
        );

        return new QueryPlan(
            sql.toString(),
            parameters
        );
    }

    private void appendStatusFilter(
        StringBuilder sql,
        List<Object> parameters,
        PublicationSearchCriteria criteria
    ) {

        if (criteria.status() == null) {
            return;
        }

        sql.append(
            """
            AND pub.status = ?
            """
        );

        parameters.add(
            criteria.status()
                .name()
        );
    }

    private void appendAsinFilter(
        StringBuilder sql,
        List<Object> parameters,
        PublicationSearchCriteria criteria
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

    private void appendEvaluationFilter(
        StringBuilder sql,
        List<Object> parameters,
        PublicationSearchCriteria criteria
    ) {

        if (criteria.dealEvaluationId() == null) {
            return;
        }

        sql.append(
            """
            AND pub.deal_evaluation_id = ?
            """
        );

        parameters.add(
            criteria.dealEvaluationId()
        );
    }

    private void appendCreationPeriodFilters(
        StringBuilder sql,
        List<Object> parameters,
        PublicationSearchCriteria criteria
    ) {

        if (criteria.createdFrom() != null) {

            sql.append(
                """
                AND pub.created_at >= ?
                """
            );

            parameters.add(
                criteria.createdFrom()
            );
        }

        if (criteria.createdUntil() != null) {

            sql.append(
                """
                AND pub.created_at <= ?
                """
            );

            parameters.add(
                criteria.createdUntil()
            );
        }
    }

    private void appendCursorFilter(
        StringBuilder sql,
        List<Object> parameters,
        PublicationSearchCriteria criteria
    ) {

        PublicationCursor cursor =
            criteria.after();

        if (cursor == null) {
            return;
        }

        sql.append(
            """
            AND (pub.created_at, pub.id) < (?, ?)
            """
        );

        parameters.add(
            cursor.createdAt()
        );

        parameters.add(
            cursor.publicationId()
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

            if (value instanceof String stringValue) {

                statement.setString(
                    parameterIndex,
                    stringValue
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
                    "Unsupported Publication query parameter type: "
                        + value.getClass()
                        .getName()
                );
            }
        }
    }

    private PublicationPage readPage(
        ResultSet resultSet,
        int requestedLimit
    ) throws SQLException {

        List<PublicationSummary> items =
            new ArrayList<>(
                requestedLimit + 1
            );

        while (resultSet.next()) {

            items.add(
                readSummary(
                    resultSet
                )
            );
        }

        boolean hasMore =
            items.size() > requestedLimit;

        if (hasMore) {

            items.remove(
                items.size() - 1
            );
        }

        PublicationCursor nextCursor =
            null;

        if (hasMore) {

            PublicationSummary last =
                items.get(
                    items.size() - 1
                );

            nextCursor =
                new PublicationCursor(
                    last.createdAt(),
                    last.publicationId()
                );
        }

        return new PublicationPage(
            items,
            nextCursor
        );
    }

    private PublicationSummary readSummary(
        ResultSet resultSet
    ) throws SQLException {

        return new PublicationSummary(
            resultSet.getLong(
                "publication_id"
            ),
            resultSet.getLong(
                "deal_evaluation_id"
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
            resultSet.getString(
                "status"
            ),
            resultSet.getString(
                "template_version"
            ),
            resultSet.getString(
                "commercial_presentation_version"
            ),
            resultSet.getString(
                "affiliate_link_version"
            ),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            )
        );
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
