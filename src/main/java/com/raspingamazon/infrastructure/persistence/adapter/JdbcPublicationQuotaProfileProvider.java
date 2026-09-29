package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.selection.port.PublicationQuotaProfileProvider;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Implementação JDBC da fonte de configuração de quota
 * operacional de publicação.
 *
 * <p>O perfil ativo é resolvido pelo escopo:</p>
 *
 * <pre>
 * channel + destination
 * </pre>
 *
 * <p>A ZoneId persistida é convertida para o tipo de domínio
 * antes de o perfil ser devolvido à aplicação.</p>
 */
public final class JdbcPublicationQuotaProfileProvider
    implements PublicationQuotaProfileProvider {

    private final Connection connection;

    public JdbcPublicationQuotaProfileProvider(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public PublicationQuotaProfile activeProfile(
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
                max_publications_per_day,
                quota_zone
            FROM publication_quota_profile
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
                        "No active publication quota profile "
                            + "was found for channel "
                            + validatedChannel
                            + " and destination "
                            + validatedDestination
                    );
                }

                PublicationQuotaProfile profile =
                    mapProfile(
                        resultSet
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one active publication quota "
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
                "Failed to load active publication quota profile",
                exception
            );
        }
    }

    private PublicationQuotaProfile mapProfile(
        ResultSet resultSet
    ) throws SQLException {

        String quotaZoneValue =
            resultSet.getString(
                "quota_zone"
            );

        ZoneId quotaZone;

        try {

            quotaZone =
                ZoneId.of(
                    quotaZoneValue
                );

        } catch (DateTimeException exception) {

            throw new IllegalStateException(
                "Persisted publication quota profile "
                    + "contains invalid quota zone: "
                    + quotaZoneValue,
                exception
            );
        }

        return new PublicationQuotaProfile(
            resultSet.getString(
                "version"
            ),
            resultSet.getInt(
                "max_publications_per_day"
            ),
            quotaZone
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

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return value;
    }
}
