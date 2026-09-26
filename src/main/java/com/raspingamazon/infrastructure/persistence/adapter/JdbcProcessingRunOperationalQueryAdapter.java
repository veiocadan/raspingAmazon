package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.orchestration.run.ProcessingRunCursor;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPage;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunSearchCriteria;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunSummary;
import com.raspingamazon.application.operation.orchestration.run.port.ProcessingRunOperationalQueryPort;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
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
 * Consulta JDBC paginada de ProcessingRun para uso operacional.
 */
public final class JdbcProcessingRunOperationalQueryAdapter
    implements ProcessingRunOperationalQueryPort {

    private final Connection connection;

    public JdbcProcessingRunOperationalQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public ProcessingRunPage search(
        ProcessingRunSearchCriteria criteria
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
                "Could not search operational ProcessingRun records",
                exception
            );
        }
    }

    private QueryPlan buildQuery(
        ProcessingRunSearchCriteria criteria
    ) {

        StringBuilder sql =
            new StringBuilder(
                """
                SELECT
                    id,
                    run_key,
                    source_uri,
                    status,
                    requested_at,
                    started_at,
                    completed_at,
                    last_error_code,
                    last_error_message,
                    created_at,
                    updated_at
                FROM processing_run
                WHERE 1 = 1
                """
            );

        List<Object> parameters =
            new ArrayList<>();

        if (criteria.status() != null) {

            sql.append(
                """
                AND status = ?
                """
            );

            parameters.add(
                criteria.status()
                    .name()
            );
        }

        if (criteria.requestedFrom() != null) {

            sql.append(
                """
                AND requested_at >= ?
                """
            );

            parameters.add(
                criteria.requestedFrom()
            );
        }

        if (criteria.requestedUntil() != null) {

            sql.append(
                """
                AND requested_at <= ?
                """
            );

            parameters.add(
                criteria.requestedUntil()
            );
        }

        ProcessingRunCursor cursor =
            criteria.after();

        if (cursor != null) {

            sql.append(
                """
                AND (requested_at, id) < (?, ?)
                """
            );

            parameters.add(
                cursor.requestedAt()
            );

            parameters.add(
                cursor.runId()
            );
        }

        sql.append(
            """
            ORDER BY
                requested_at DESC,
                id DESC
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
                    "Unsupported ProcessingRun query parameter type: "
                        + value.getClass()
                        .getName()
                );
            }
        }
    }

    private ProcessingRunPage readPage(
        ResultSet resultSet,
        int requestedLimit
    ) throws SQLException {

        List<ProcessingRunSummary> items =
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

        ProcessingRunCursor nextCursor =
            null;

        if (hasMore) {

            ProcessingRunSummary last =
                items.get(
                    items.size() - 1
                );

            nextCursor =
                new ProcessingRunCursor(
                    last.requestedAt(),
                    last.runId()
                );
        }

        return new ProcessingRunPage(
            items,
            nextCursor
        );
    }

    private ProcessingRunSummary readSummary(
        ResultSet resultSet
    ) throws SQLException {

        return new ProcessingRunSummary(
            resultSet.getLong(
                "id"
            ),
            resultSet.getString(
                "run_key"
            ),
            resultSet.getString(
                "source_uri"
            ),
            ProcessingRunStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            resultSet.getObject(
                "requested_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "started_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "completed_at",
                OffsetDateTime.class
            ),
            resultSet.getString(
                "last_error_code"
            ),
            resultSet.getString(
                "last_error_message"
            ),
            resultSet.getObject(
                "created_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "updated_at",
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
