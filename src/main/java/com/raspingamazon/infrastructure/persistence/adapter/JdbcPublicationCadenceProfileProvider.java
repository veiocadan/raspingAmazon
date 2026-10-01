package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.scheduling.port.PublicationCadenceProfileProvider;
import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Implementação PostgreSQL da leitura do perfil ativo
 * de cadência de publicação.
 */
public final class JdbcPublicationCadenceProfileProvider
    implements PublicationCadenceProfileProvider {

    private final Connection connection;

    public JdbcPublicationCadenceProfileProvider(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public PublicationCadenceProfile activeProfile(
        String channel,
        String destination
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

        String sql =
            """
            SELECT
                version,
                interval_seconds,
                window_start,
                window_end,
                cadence_zone
            FROM publication_cadence_profile
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
                validatedChannel
            );

            statement.setString(
                2,
                validatedDestination
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                if (!resultSet.next()) {

                    throw new IllegalStateException(
                        "No active publication cadence profile "
                            + "was found for channel "
                            + validatedChannel
                            + " and destination "
                            + validatedDestination
                    );
                }

                PublicationCadenceProfile profile =
                    mapProfile(
                        resultSet
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one active publication cadence "
                            + "profile was found for channel "
                            + validatedChannel
                            + " and destination "
                            + validatedDestination
                    );
                }

                return profile;
            }

        } catch (SQLException exception) {

            throw new IllegalStateException(
                "Failed to load active publication cadence profile",
                exception
            );
        }
    }

    private PublicationCadenceProfile mapProfile(
        ResultSet resultSet
    ) throws SQLException {

        String zoneValue =
            resultSet.getString(
                "cadence_zone"
            );

        final ZoneId zone;

        try {

            zone =
                ZoneId.of(
                    zoneValue
                );

        } catch (DateTimeException exception) {

            throw new IllegalStateException(
                "Persisted publication cadence profile "
                    + "contains invalid cadence zone: "
                    + zoneValue,
                exception
            );
        }

        return new PublicationCadenceProfile(
            resultSet.getString(
                "version"
            ),
            Duration.ofSeconds(
                resultSet.getLong(
                    "interval_seconds"
                )
            ),
            resultSet.getObject(
                "window_start",
                LocalTime.class
            ),
            resultSet.getObject(
                "window_end",
                LocalTime.class
            ),
            zone
        );
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
}
