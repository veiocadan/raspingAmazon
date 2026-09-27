package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.scheduling.ProcessingSchedule;
import com.raspingamazon.application.scheduling.ProcessingScheduleLease;
import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementação JDBC da persistência e coordenação dos agendamentos
 * recorrentes.
 *
 * <p>O PostgreSQL é utilizado como fonte de verdade do estado
 * operacional e como coordenador do lease distribuído.</p>
 *
 * <p>Não existe lock em memória. A aquisição é feita por UPDATE
 * condicional e atômico.</p>
 */
public final class JdbcProcessingScheduleAdapter
    implements ProcessingSchedulePort {

    private final Connection connection;

    public JdbcProcessingScheduleAdapter(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public ProcessingSchedule saveIfAbsent(
        ProcessingSchedule schedule
    ) {

        Objects.requireNonNull(
            schedule,
            "schedule must not be null"
        );

        long intervalMs =
            durationToMilliseconds(
                schedule.interval(),
                "ProcessingSchedule interval"
            );

        String sql =
            """
            INSERT INTO processing_schedule (
                schedule_key,
                source_uri,
                enabled,
                interval_ms,
                next_run_at,
                lease_owner,
                lease_expires_at,
                last_scheduled_for,
                last_processing_run_id,
                created_at,
                updated_at
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                NULL,
                NULL,
                NULL,
                NULL,
                ?,
                ?
            )
            ON CONFLICT (schedule_key)
            DO NOTHING
            RETURNING
                schedule_key,
                source_uri,
                enabled,
                interval_ms,
                next_run_at,
                lease_owner,
                lease_expires_at,
                last_scheduled_for,
                last_processing_run_id,
                created_at,
                updated_at
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                schedule.scheduleKey()
            );

            statement.setString(
                2,
                schedule.source().toString()
            );

            statement.setBoolean(
                3,
                schedule.enabled()
            );

            statement.setLong(
                4,
                intervalMs
            );

            statement.setObject(
                5,
                schedule.nextRunAt()
            );

            statement.setObject(
                6,
                schedule.createdAt()
            );

            statement.setObject(
                7,
                schedule.updatedAt()
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (resultSet.next()) {
                    return readSchedule(
                        resultSet
                    );
                }
            }

        } catch (SQLException exception) {
            throw persistenceFailure(
                "save ProcessingSchedule",
                exception
            );
        }

        ProcessingSchedule existing =
            findByKey(
                schedule.scheduleKey()
            ).orElseThrow(
                () -> new IllegalStateException(
                    "ProcessingSchedule disappeared after conflict: "
                        + schedule.scheduleKey()
                )
            );

        validateExistingIdentity(
            schedule,
            existing
        );

        return existing;
    }

    @Override
    public Optional<ProcessingSchedule> findByKey(
        String scheduleKey
    ) {

        String validatedScheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        String sql =
            """
            SELECT
                schedule_key,
                source_uri,
                enabled,
                interval_ms,
                next_run_at,
                lease_owner,
                lease_expires_at,
                last_scheduled_for,
                last_processing_run_id,
                created_at,
                updated_at
            FROM processing_schedule
            WHERE schedule_key = ?
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                validatedScheduleKey
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                    readSchedule(
                        resultSet
                    )
                );
            }

        } catch (SQLException exception) {
            throw persistenceFailure(
                "find ProcessingSchedule",
                exception
            );
        }
    }

    @Override
    public Optional<ProcessingScheduleLease> tryAcquireDue(
        String scheduleKey,
        String leaseOwner,
        OffsetDateTime now,
        Duration leaseDuration
    ) {

        String validatedScheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        String validatedLeaseOwner =
            requireText(
                leaseOwner,
                "leaseOwner must not be blank"
            );

        Objects.requireNonNull(
            now,
            "now must not be null"
        );

        requirePositiveDuration(
            leaseDuration,
            "leaseDuration"
        );

        OffsetDateTime leaseExpiresAt =
            addDuration(
                now,
                leaseDuration
            );

        String sql =
            """
            UPDATE processing_schedule
            SET
                lease_owner = ?,
                lease_expires_at = ?,
                updated_at = ?
            WHERE schedule_key = ?
              AND enabled = TRUE
              AND next_run_at <= ?
              AND (
                  lease_owner IS NULL
                  OR lease_expires_at <= ?
              )
            RETURNING
                schedule_key,
                source_uri,
                enabled,
                interval_ms,
                next_run_at,
                lease_owner,
                lease_expires_at,
                last_scheduled_for,
                last_processing_run_id,
                created_at,
                updated_at
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                validatedLeaseOwner
            );

            statement.setObject(
                2,
                leaseExpiresAt
            );

            statement.setObject(
                3,
                now
            );

            statement.setString(
                4,
                validatedScheduleKey
            );

            statement.setObject(
                5,
                now
            );

            statement.setObject(
                6,
                now
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                ProcessingSchedule acquired =
                    readSchedule(
                        resultSet
                    );

                return Optional.of(
                    new ProcessingScheduleLease(
                        acquired.scheduleKey(),
                        acquired.source(),
                        acquired.interval(),
                        acquired.nextRunAt(),
                        acquired.leaseOwner(),
                        acquired.leaseExpiresAt()
                    )
                );
            }

        } catch (SQLException exception) {
            throw persistenceFailure(
                "acquire ProcessingSchedule lease",
                exception
            );
        }
    }

    @Override
    public ProcessingSchedule confirmScheduled(
        String scheduleKey,
        String leaseOwner,
        OffsetDateTime scheduledFor,
        long processingRunId,
        OffsetDateTime nextRunAt,
        OffsetDateTime confirmedAt
    ) {

        String validatedScheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        String validatedLeaseOwner =
            requireText(
                leaseOwner,
                "leaseOwner must not be blank"
            );

        Objects.requireNonNull(
            scheduledFor,
            "scheduledFor must not be null"
        );

        requirePositiveId(
            processingRunId,
            "processingRunId"
        );

        Objects.requireNonNull(
            nextRunAt,
            "nextRunAt must not be null"
        );

        Objects.requireNonNull(
            confirmedAt,
            "confirmedAt must not be null"
        );

        if (!nextRunAt.isAfter(
            scheduledFor
        )) {

            throw new IllegalArgumentException(
                "nextRunAt must be after scheduledFor"
            );
        }

        if (confirmedAt.isBefore(
            scheduledFor
        )) {

            throw new IllegalArgumentException(
                "confirmedAt must not be before scheduledFor"
            );
        }

        String sql =
            """
            UPDATE processing_schedule
            SET
                next_run_at = ?,
                lease_owner = NULL,
                lease_expires_at = NULL,
                last_scheduled_for = ?,
                last_processing_run_id = ?,
                updated_at = ?
            WHERE schedule_key = ?
              AND lease_owner = ?
              AND lease_expires_at > ?
              AND next_run_at = ?
            RETURNING
                schedule_key,
                source_uri,
                enabled,
                interval_ms,
                next_run_at,
                lease_owner,
                lease_expires_at,
                last_scheduled_for,
                last_processing_run_id,
                created_at,
                updated_at
            """;

        return executeRequiredUpdate(
            sql,
            statement -> {

                statement.setObject(
                    1,
                    nextRunAt
                );

                statement.setObject(
                    2,
                    scheduledFor
                );

                statement.setLong(
                    3,
                    processingRunId
                );

                statement.setObject(
                    4,
                    confirmedAt
                );

                statement.setString(
                    5,
                    validatedScheduleKey
                );

                statement.setString(
                    6,
                    validatedLeaseOwner
                );

                statement.setObject(
                    7,
                    confirmedAt
                );

                statement.setObject(
                    8,
                    scheduledFor
                );
            },
            "ProcessingSchedule window cannot be confirmed"
        );
    }

    @Override
    public ProcessingSchedule releaseLease(
        String scheduleKey,
        String leaseOwner,
        OffsetDateTime releasedAt
    ) {

        String validatedScheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        String validatedLeaseOwner =
            requireText(
                leaseOwner,
                "leaseOwner must not be blank"
            );

        Objects.requireNonNull(
            releasedAt,
            "releasedAt must not be null"
        );

        String sql =
            """
            UPDATE processing_schedule
            SET
                lease_owner = NULL,
                lease_expires_at = NULL,
                updated_at = ?
            WHERE schedule_key = ?
              AND lease_owner = ?
            RETURNING
                schedule_key,
                source_uri,
                enabled,
                interval_ms,
                next_run_at,
                lease_owner,
                lease_expires_at,
                last_scheduled_for,
                last_processing_run_id,
                created_at,
                updated_at
            """;

        return executeRequiredUpdate(
            sql,
            statement -> {

                statement.setObject(
                    1,
                    releasedAt
                );

                statement.setString(
                    2,
                    validatedScheduleKey
                );

                statement.setString(
                    3,
                    validatedLeaseOwner
                );
            },
            "ProcessingSchedule lease cannot be released"
        );
    }

    @Override
    public ProcessingSchedule pause(
        String scheduleKey,
        OffsetDateTime changedAt
    ) {

        String validatedScheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        Objects.requireNonNull(
            changedAt,
            "changedAt must not be null"
        );

        String sql =
            """
            UPDATE processing_schedule
            SET
                enabled = FALSE,
                updated_at = ?
            WHERE schedule_key = ?
            RETURNING
                schedule_key,
                source_uri,
                enabled,
                interval_ms,
                next_run_at,
                lease_owner,
                lease_expires_at,
                last_scheduled_for,
                last_processing_run_id,
                created_at,
                updated_at
            """;

        return executeRequiredUpdate(
            sql,
            statement -> {

                statement.setObject(
                    1,
                    changedAt
                );

                statement.setString(
                    2,
                    validatedScheduleKey
                );
            },
            "ProcessingSchedule cannot be paused"
        );
    }

    @Override
    public ProcessingSchedule resume(
        String scheduleKey,
        OffsetDateTime nextRunAt,
        OffsetDateTime changedAt
    ) {

        String validatedScheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        Objects.requireNonNull(
            nextRunAt,
            "nextRunAt must not be null"
        );

        Objects.requireNonNull(
            changedAt,
            "changedAt must not be null"
        );

        String sql =
            """
            UPDATE processing_schedule
            SET
                enabled = TRUE,
                next_run_at = ?,
                updated_at = ?
            WHERE schedule_key = ?
            RETURNING
                schedule_key,
                source_uri,
                enabled,
                interval_ms,
                next_run_at,
                lease_owner,
                lease_expires_at,
                last_scheduled_for,
                last_processing_run_id,
                created_at,
                updated_at
            """;

        return executeRequiredUpdate(
            sql,
            statement -> {

                statement.setObject(
                    1,
                    nextRunAt
                );

                statement.setObject(
                    2,
                    changedAt
                );

                statement.setString(
                    3,
                    validatedScheduleKey
                );
            },
            "ProcessingSchedule cannot be resumed"
        );
    }

    @Override
    public ProcessingSchedule changeInterval(
        String scheduleKey,
        Duration interval,
        OffsetDateTime nextRunAt,
        OffsetDateTime changedAt
    ) {

        String validatedScheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        long intervalMs =
            durationToMilliseconds(
                interval,
                "interval"
            );

        Objects.requireNonNull(
            nextRunAt,
            "nextRunAt must not be null"
        );

        Objects.requireNonNull(
            changedAt,
            "changedAt must not be null"
        );

        String sql =
            """
            UPDATE processing_schedule
            SET
                interval_ms = ?,
                next_run_at = ?,
                updated_at = ?
            WHERE schedule_key = ?
            RETURNING
                schedule_key,
                source_uri,
                enabled,
                interval_ms,
                next_run_at,
                lease_owner,
                lease_expires_at,
                last_scheduled_for,
                last_processing_run_id,
                created_at,
                updated_at
            """;

        return executeRequiredUpdate(
            sql,
            statement -> {

                statement.setLong(
                    1,
                    intervalMs
                );

                statement.setObject(
                    2,
                    nextRunAt
                );

                statement.setObject(
                    3,
                    changedAt
                );

                statement.setString(
                    4,
                    validatedScheduleKey
                );
            },
            "ProcessingSchedule interval cannot be changed"
        );
    }

    private ProcessingSchedule executeRequiredUpdate(
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

                return readSchedule(
                    resultSet
                );
            }

        } catch (SQLException exception) {
            throw persistenceFailure(
                "update ProcessingSchedule",
                exception
            );
        }
    }

    private ProcessingSchedule readSchedule(
        ResultSet resultSet
    ) throws SQLException {

        return new ProcessingSchedule(
            resultSet.getString(
                "schedule_key"
            ),
            URI.create(
                resultSet.getString(
                    "source_uri"
                )
            ),
            resultSet.getBoolean(
                "enabled"
            ),
            Duration.ofMillis(
                resultSet.getLong(
                    "interval_ms"
                )
            ),
            resultSet.getObject(
                "next_run_at",
                OffsetDateTime.class
            ),
            resultSet.getString(
                "lease_owner"
            ),
            resultSet.getObject(
                "lease_expires_at",
                OffsetDateTime.class
            ),
            resultSet.getObject(
                "last_scheduled_for",
                OffsetDateTime.class
            ),
            readNullableLong(
                resultSet,
                "last_processing_run_id"
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

    private void validateExistingIdentity(
        ProcessingSchedule requested,
        ProcessingSchedule existing
    ) {

        if (!requested.source().equals(
            existing.source()
        )) {

            throw new IllegalStateException(
                "ProcessingSchedule scheduleKey collision: "
                    + requested.scheduleKey()
            );
        }
    }

    private long durationToMilliseconds(
        Duration duration,
        String name
    ) {

        requirePositiveDuration(
            duration,
            name
        );

        final long milliseconds;

        try {
            milliseconds =
                duration.toMillis();

        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                name + " is too large",
                exception
            );
        }

        if (milliseconds <= 0) {
            throw new IllegalArgumentException(
                name + " must be at least one millisecond"
            );
        }

        if (!Duration.ofMillis(
            milliseconds
        ).equals(
            duration
        )) {

            throw new IllegalArgumentException(
                name + " must use millisecond precision"
            );
        }

        return milliseconds;
    }

    private void requirePositiveDuration(
        Duration duration,
        String name
    ) {

        Objects.requireNonNull(
            duration,
            name + " must not be null"
        );

        if (duration.isZero()
            || duration.isNegative()) {

            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }
    }

    private OffsetDateTime addDuration(
        OffsetDateTime instant,
        Duration duration
    ) {

        try {
            return instant.plus(
                duration
            );

        } catch (DateTimeException
                 | ArithmeticException exception) {

            throw new IllegalArgumentException(
                "leaseDuration cannot be represented from now",
                exception
            );
        }
    }

    private void requirePositiveId(
        long id,
        String name
    ) {

        if (id <= 0) {
            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }
    }

    private String requireText(
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
