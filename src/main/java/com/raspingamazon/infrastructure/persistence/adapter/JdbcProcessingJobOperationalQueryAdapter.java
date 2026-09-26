package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.operation.orchestration.job.ProcessingJobCursor;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobPage;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobSearchCriteria;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobSummary;
import com.raspingamazon.application.operation.orchestration.job.port.ProcessingJobOperationalQueryPort;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobType;
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
 * Consulta JDBC paginada de ProcessingJob para uso operacional.
 *
 * <p>Este adapter é somente leitura. Claim, retry, sucesso e
 * encerramento DEAD continuam pertencendo ao adapter da fila.</p>
 */
public final class JdbcProcessingJobOperationalQueryAdapter
    implements ProcessingJobOperationalQueryPort {

    private final Connection connection;

    public JdbcProcessingJobOperationalQueryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public ProcessingJobPage search(
        ProcessingJobSearchCriteria criteria
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
                "Could not search operational ProcessingJob records",
                exception
            );
        }
    }

    private QueryPlan buildQuery(
        ProcessingJobSearchCriteria criteria
    ) {

        StringBuilder sql =
            new StringBuilder(
                """
                SELECT
                    id,
                    job_type,
                    status,
                    processing_run_id,
                    deal_candidate_id,
                    offer_snapshot_id,
                    idempotency_key,
                    attempt_count,
                    max_attempts,
                    available_at,
                    locked_at,
                    locked_by,
                    last_failure_type,
                    last_error_code,
                    last_error_message,
                    created_at,
                    updated_at,
                    finished_at
                FROM processing_job
                WHERE 1 = 1
                """
            );

        List<Object> parameters =
            new ArrayList<>();

        appendTypeFilter(
            sql,
            parameters,
            criteria
        );

        appendStatusFilter(
            sql,
            parameters,
            criteria
        );

        appendFailureFilter(
            sql,
            parameters,
            criteria
        );

        appendSubjectFilters(
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
                created_at DESC,
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

    private void appendTypeFilter(
        StringBuilder sql,
        List<Object> parameters,
        ProcessingJobSearchCriteria criteria
    ) {

        if (criteria.type() == null) {
            return;
        }

        sql.append(
            """
            AND job_type = ?
            """
        );

        parameters.add(
            criteria.type()
                .name()
        );
    }

    private void appendStatusFilter(
        StringBuilder sql,
        List<Object> parameters,
        ProcessingJobSearchCriteria criteria
    ) {

        if (criteria.status() == null) {
            return;
        }

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

    private void appendFailureFilter(
        StringBuilder sql,
        List<Object> parameters,
        ProcessingJobSearchCriteria criteria
    ) {

        if (criteria.lastFailureType() == null) {
            return;
        }

        sql.append(
            """
            AND last_failure_type = ?
            """
        );

        parameters.add(
            criteria.lastFailureType()
                .name()
        );
    }

    private void appendSubjectFilters(
        StringBuilder sql,
        List<Object> parameters,
        ProcessingJobSearchCriteria criteria
    ) {

        if (criteria.processingRunId() != null) {

            sql.append(
                """
                AND processing_run_id = ?
                """
            );

            parameters.add(
                criteria.processingRunId()
            );
        }

        if (criteria.dealCandidateId() != null) {

            sql.append(
                """
                AND deal_candidate_id = ?
                """
            );

            parameters.add(
                criteria.dealCandidateId()
            );
        }

        if (criteria.offerSnapshotId() != null) {

            sql.append(
                """
                AND offer_snapshot_id = ?
                """
            );

            parameters.add(
                criteria.offerSnapshotId()
            );
        }
    }

    private void appendCreationPeriodFilters(
        StringBuilder sql,
        List<Object> parameters,
        ProcessingJobSearchCriteria criteria
    ) {

        if (criteria.createdFrom() != null) {

            sql.append(
                """
                AND created_at >= ?
                """
            );

            parameters.add(
                criteria.createdFrom()
            );
        }

        if (criteria.createdUntil() != null) {

            sql.append(
                """
                AND created_at <= ?
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
        ProcessingJobSearchCriteria criteria
    ) {

        ProcessingJobCursor cursor =
            criteria.after();

        if (cursor == null) {
            return;
        }

        sql.append(
            """
            AND (created_at, id) < (?, ?)
            """
        );

        parameters.add(
            cursor.createdAt()
        );

        parameters.add(
            cursor.jobId()
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
                    "Unsupported ProcessingJob query parameter type: "
                        + value.getClass()
                        .getName()
                );
            }
        }
    }

    private ProcessingJobPage readPage(
        ResultSet resultSet,
        int requestedLimit
    ) throws SQLException {

        List<ProcessingJobSummary> items =
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

        ProcessingJobCursor nextCursor =
            null;

        if (hasMore) {

            ProcessingJobSummary last =
                items.get(
                    items.size() - 1
                );

            nextCursor =
                new ProcessingJobCursor(
                    last.createdAt(),
                    last.jobId()
                );
        }

        return new ProcessingJobPage(
            items,
            nextCursor
        );
    }

    private ProcessingJobSummary readSummary(
        ResultSet resultSet
    ) throws SQLException {

        return new ProcessingJobSummary(
            resultSet.getLong(
                "id"
            ),
            ProcessingJobType.valueOf(
                resultSet.getString(
                    "job_type"
                )
            ),
            ProcessingJobStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            readNullableLong(
                resultSet,
                "processing_run_id"
            ),
            readNullableLong(
                resultSet,
                "deal_candidate_id"
            ),
            readNullableLong(
                resultSet,
                "offer_snapshot_id"
            ),
            resultSet.getString(
                "idempotency_key"
            ),
            resultSet.getInt(
                "attempt_count"
            ),
            resultSet.getInt(
                "max_attempts"
            ),
            resultSet.getObject(
                "available_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "locked_at",
                OffsetDateTime.class
            ),
            resultSet.getString(
                "locked_by"
            ),
            readFailureType(
                resultSet
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
            ),
            resultSet.getObject(
                "finished_at",
                OffsetDateTime.class
            )
        );
    }

    private ProcessingFailureType readFailureType(
        ResultSet resultSet
    ) throws SQLException {

        String persistedValue =
            resultSet.getString(
                "last_failure_type"
            );

        if (persistedValue == null) {
            return null;
        }

        return ProcessingFailureType.valueOf(
            persistedValue
        );
    }

    private Long readNullableLong(
        ResultSet resultSet,
        String column
    ) throws SQLException {

        long value =
            resultSet.getLong(
                column
            );

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
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
