package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Sincroniza no PostgreSQL o estado desejado da cadência
 * de publicação.
 *
 * <p>Política:</p>
 *
 * <pre>
 * versão inexistente
 *     -> cria nova versão ativa
 *     -> desativa versão anterior
 *
 * mesma versão ativa + mesmos valores
 *     -> operação idempotente
 *
 * mesma versão ativa + valores diferentes
 *     -> erro
 *
 * versão histórica
 *     -> erro
 *     -> nunca é reativada
 * </pre>
 */
public final class JdbcPublicationCadenceProfileSynchronizer {

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    public JdbcPublicationCadenceProfileSynchronizer(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );

        this.transactionAdapter =
            new JdbcTransactionAdapter(
                connection
            );
    }

    public PublicationCadenceProfile synchronize(
        String channel,
        String destination,
        PublicationCadenceProfile desiredProfile
    ) {

        String validatedChannel =
            requireText(
                channel,
                "channel"
            );

        String validatedDestination =
            requireText(
                destination,
                "destination"
            );

        Objects.requireNonNull(
            desiredProfile,
            "desiredProfile must not be null"
        );

        return transactionAdapter.execute(
            () ->
                synchronizeInsideTransaction(
                    validatedChannel,
                    validatedDestination,
                    desiredProfile
                )
        );
    }

    private PublicationCadenceProfile synchronizeInsideTransaction(
        String channel,
        String destination,
        PublicationCadenceProfile desiredProfile
    ) {

        try {

            StoredProfile stored =
                findVersion(
                    channel,
                    destination,
                    desiredProfile.version()
                );

            if (stored != null) {

                validateImmutableVersion(
                    desiredProfile,
                    stored
                );

                if (stored.active()) {

                    return desiredProfile;
                }

                throw new IllegalStateException(
                    "Publication cadence version "
                        + desiredProfile.version()
                        + " is historical and cannot be reactivated. "
                        + "Use a new version."
                );
            }

            deactivateCurrentProfile(
                channel,
                destination
            );

            insertProfile(
                channel,
                destination,
                desiredProfile
            );

            return desiredProfile;

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to synchronize publication cadence profile",
                exception
            );
        }
    }

    private StoredProfile findVersion(
        String channel,
        String destination,
        String version
    ) throws SQLException {

        String sql =
            """
            SELECT
                interval_seconds,
                window_start,
                window_end,
                cadence_zone,
                active
            FROM publication_cadence_profile
            WHERE channel = ?
              AND destination = ?
              AND version = ?
            FOR UPDATE
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                channel
            );

            statement.setString(
                2,
                destination
            );

            statement.setString(
                3,
                version
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    return null;
                }

                StoredProfile stored =
                    new StoredProfile(
                        resultSet.getLong(
                            "interval_seconds"
                        ),
                        resultSet.getObject(
                            "window_start",
                            LocalTime.class
                        ),
                        resultSet.getObject(
                            "window_end",
                            LocalTime.class
                        ),
                        resultSet.getString(
                            "cadence_zone"
                        ),
                        resultSet.getBoolean(
                            "active"
                        )
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one publication cadence profile "
                            + "exists for the same scope and version"
                    );
                }

                return stored;
            }
        }
    }

    private void validateImmutableVersion(
        PublicationCadenceProfile desired,
        StoredProfile stored
    ) {

        boolean sameInterval =
            desired.interval()
                .toSeconds()
                == stored.intervalSeconds();

        boolean sameStart =
            desired.windowStart()
                .equals(
                    stored.windowStart()
                );

        boolean sameEnd =
            desired.windowEnd()
                .equals(
                    stored.windowEnd()
                );

        boolean sameZone =
            desired.zone()
                .getId()
                .equals(
                    stored.cadenceZone()
                );

        if (!sameInterval
            || !sameStart
            || !sameEnd
            || !sameZone) {

            throw new IllegalStateException(
                "Publication cadence version "
                    + desired.version()
                    + " already exists with different values. "
                    + "Versions are immutable; use a new version."
            );
        }
    }

    private void deactivateCurrentProfile(
        String channel,
        String destination
    ) throws SQLException {

        String sql =
            """
            UPDATE publication_cadence_profile
            SET active = false
            WHERE channel = ?
              AND destination = ?
              AND active = true
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                channel
            );

            statement.setString(
                2,
                destination
            );

            statement.executeUpdate();
        }
    }

    private void insertProfile(
        String channel,
        String destination,
        PublicationCadenceProfile profile
    ) throws SQLException {

        String sql =
            """
            INSERT INTO publication_cadence_profile (
                channel,
                destination,
                version,
                interval_seconds,
                window_start,
                window_end,
                cadence_zone,
                active
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                true
            )
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                channel
            );

            statement.setString(
                2,
                destination
            );

            statement.setString(
                3,
                profile.version()
            );

            statement.setLong(
                4,
                profile.interval()
                    .toSeconds()
            );

            statement.setObject(
                5,
                profile.windowStart()
            );

            statement.setObject(
                6,
                profile.windowEnd()
            );

            statement.setString(
                7,
                profile.zone()
                    .getId()
            );

            int inserted =
                statement.executeUpdate();

            if (inserted != 1) {

                throw new IllegalStateException(
                    "Publication cadence profile insert affected "
                        + "an unexpected number of rows: "
                        + inserted
                );
            }
        }
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return trimmed;
    }

    private record StoredProfile(
        long intervalSeconds,
        LocalTime windowStart,
        LocalTime windowEnd,
        String cadenceZone,
        boolean active
    ) {

        private StoredProfile {

            if (intervalSeconds <= 0L) {

                throw new IllegalStateException(
                    "Persisted cadence interval must be positive"
                );
            }

            Objects.requireNonNull(
                windowStart,
                "windowStart must not be null"
            );

            Objects.requireNonNull(
                windowEnd,
                "windowEnd must not be null"
            );

            Objects.requireNonNull(
                cadenceZone,
                "cadenceZone must not be null"
            );

            if (windowStart.equals(
                windowEnd
            )) {

                throw new IllegalStateException(
                    "Persisted cadence window boundaries "
                        + "must not be equal"
                );
            }

            try {

                ZoneId.of(
                    cadenceZone
                );

            } catch (DateTimeException exception) {

                throw new IllegalStateException(
                    "Persisted cadence zone is invalid: "
                        + cadenceZone,
                    exception
                );
            }
        }
    }
}
