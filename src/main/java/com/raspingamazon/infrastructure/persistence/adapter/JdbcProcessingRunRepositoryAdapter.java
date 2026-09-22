package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.orchestration.ProcessingRun;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.orchestration.port.ProcessingRunRepositoryPort;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação JDBC da persistência de ProcessingRun.
 */
public final class JdbcProcessingRunRepositoryAdapter
    implements ProcessingRunRepositoryPort {

    private final Connection connection;

    public JdbcProcessingRunRepositoryAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public ProcessingRun save(
        ProcessingRun run
    ) {

        Objects.requireNonNull(
            run,
            "run must not be null"
        );

        if (run.persisted()) {
            throw new IllegalArgumentException(
                "New ProcessingRun must not already have an id"
            );
        }

        if (run.status()
            != ProcessingRunStatus.PENDING) {

            throw new IllegalArgumentException(
                "New ProcessingRun must start as PENDING"
            );
        }

        String sql =
            """
            INSERT INTO processing_run (
                run_key,
                source_uri,
                status,
                requested_at,
                started_at,
                completed_at,
                last_error_code,
                last_error_message
            )
            VALUES (
                ?,
                ?,
                'PENDING',
                ?,
                NULL,
                NULL,
                NULL,
                NULL
            )
            ON CONFLICT (run_key)
            DO UPDATE
            SET run_key =
                EXCLUDED.run_key
            RETURNING
                id,
                run_key,
                source_uri,
                status,
                requested_at,
                started_at,
                completed_at,
                last_error_code,
                last_error_message
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                run.runKey()
            );

            statement.setString(
                2,
                run.source().toString()
            );

            statement.setObject(
                3,
                run.requestedAt()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        "ProcessingRun insert returned no row"
                    );
                }

                ProcessingRun persisted =
                    readRun(
                        resultSet
                    );

                validateIdempotentMatch(
                    run,
                    persisted
                );

                return persisted;
            }

        } catch (SQLException exception) {
            throw persistenceFailure(
                "save ProcessingRun",
                exception
            );
        }
    }

    @Override
    public Optional<ProcessingRun> findById(
        long id
    ) {

        requirePositiveId(
            id
        );

        String sql =
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
                last_error_message
            FROM processing_run
            WHERE id = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setLong(
                1,
                id
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    readRun(
                        resultSet
                    )
                );
            }

        } catch (SQLException exception) {
            throw persistenceFailure(
                "find ProcessingRun",
                exception
            );
        }
    }

    @Override
    public ProcessingRun markRunning(
        long id,
        OffsetDateTime startedAt
    ) {

        requirePositiveId(
            id
        );

        Objects.requireNonNull(
            startedAt,
            "startedAt must not be null"
        );

        String sql =
            """
            UPDATE processing_run
            SET
                status = 'RUNNING',
                started_at = ?,
                completed_at = NULL,
                last_error_code = NULL,
                last_error_message = NULL,
                updated_at = ?
            WHERE id = ?
              AND status IN (
                  'PENDING',
                  'FAILED'
              )
            RETURNING
                id,
                run_key,
                source_uri,
                status,
                requested_at,
                started_at,
                completed_at,
                last_error_code,
                last_error_message
            """;

        return executeStateUpdate(
            sql,
            statement -> {

                statement.setObject(
                    1,
                    startedAt
                );

                statement.setObject(
                    2,
                    startedAt
                );

                statement.setLong(
                    3,
                    id
                );
            },
            "ProcessingRun cannot be marked RUNNING"
        );
    }

    @Override
    public ProcessingRun markCompleted(
        long id,
        OffsetDateTime completedAt
    ) {

        requirePositiveId(
            id
        );

        Objects.requireNonNull(
            completedAt,
            "completedAt must not be null"
        );

        String sql =
            """
            UPDATE processing_run
            SET
                status = 'COMPLETED',
                completed_at = ?,
                last_error_code = NULL,
                last_error_message = NULL,
                updated_at = ?
            WHERE id = ?
              AND status = 'RUNNING'
              AND started_at IS NOT NULL
              AND ? >= started_at
            RETURNING
                id,
                run_key,
                source_uri,
                status,
                requested_at,
                started_at,
                completed_at,
                last_error_code,
                last_error_message
            """;

        return executeStateUpdate(
            sql,
            statement -> {

                statement.setObject(
                    1,
                    completedAt
                );

                statement.setObject(
                    2,
                    completedAt
                );

                statement.setLong(
                    3,
                    id
                );

                statement.setObject(
                    4,
                    completedAt
                );
            },
            "ProcessingRun cannot be marked COMPLETED"
        );
    }

    @Override
    public ProcessingRun markFailed(
        long id,
        String errorCode,
        String errorMessage,
        OffsetDateTime failedAt
    ) {

        requirePositiveId(
            id
        );

        errorCode =
            requireNonBlank(
                errorCode,
                "errorCode must not be blank"
            );

        Objects.requireNonNull(
            failedAt,
            "failedAt must not be null"
        );

        String sql =
            """
            UPDATE processing_run
            SET
                status = 'FAILED',
                completed_at = ?,
                last_error_code = ?,
                last_error_message = ?,
                updated_at = ?
            WHERE id = ?
              AND status = 'RUNNING'
              AND started_at IS NOT NULL
              AND ? >= started_at
            RETURNING
                id,
                run_key,
                source_uri,
                status,
                requested_at,
                started_at,
                completed_at,
                last_error_code,
                last_error_message
            """;

        String finalErrorCode =
            errorCode;

        return executeStateUpdate(
            sql,
            statement -> {

                statement.setObject(
                    1,
                    failedAt
                );

                statement.setString(
                    2,
                    finalErrorCode
                );

                statement.setString(
                    3,
                    errorMessage
                );

                statement.setObject(
                    4,
                    failedAt
                );

                statement.setLong(
                    5,
                    id
                );

                statement.setObject(
                    6,
                    failedAt
                );
            },
            "ProcessingRun cannot be marked FAILED"
        );
    }

    private ProcessingRun executeStateUpdate(
        String sql,
        StatementBinder binder,
        String noRowMessage
    ) {

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            binder.bind(
                statement
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new IllegalStateException(
                        noRowMessage
                    );
                }

                return readRun(
                    resultSet
                );
            }

        } catch (SQLException exception) {
            throw persistenceFailure(
                "update ProcessingRun state",
                exception
            );
        }
    }

    private ProcessingRun readRun(
        ResultSet resultSet
    ) throws SQLException {

        return new ProcessingRun(
            resultSet.getLong(
                "id"
            ),
            resultSet.getString(
                "run_key"
            ),
            URI.create(
                resultSet.getString(
                    "source_uri"
                )
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
            )
        );
    }

    private void validateIdempotentMatch(
        ProcessingRun requested,
        ProcessingRun persisted
    ) {

        boolean sameLogicalRun =
            requested.runKey()
                .equals(
                    persisted.runKey()
                )
                && requested.source()
                .equals(
                    persisted.source()
                )
                && requested.requestedAt()
                .toInstant()
                .equals(
                    persisted.requestedAt()
                        .toInstant()
                );

        if (!sameLogicalRun) {
            throw new IllegalStateException(
                "ProcessingRun runKey collision: "
                    + requested.runKey()
            );
        }
    }

    private void requirePositiveId(
        long id
    ) {

        if (id <= 0) {
            throw new IllegalArgumentException(
                "ProcessingRun id must be positive"
            );
        }
    }

    private String requireNonBlank(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }

    private IllegalStateException persistenceFailure(
        String operation,
        SQLException exception
    ) {

        return new IllegalStateException(
            "Could not "
                + operation,
            exception
        );
    }

    @FunctionalInterface
    private interface StatementBinder {

        void bind(
            PreparedStatement statement
        ) throws SQLException;
    }
}
