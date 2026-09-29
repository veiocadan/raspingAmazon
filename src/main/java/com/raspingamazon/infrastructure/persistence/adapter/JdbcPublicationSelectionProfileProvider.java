package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.publication.selection.port.PublicationSelectionProfileProvider;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;

/**
 * Implementação JDBC da fonte de configuração temporal
 * da seleção operacional de publicações.
 *
 * <p>O perfil ativo é resolvido pelo escopo:</p>
 *
 * <pre>
 * channel + destination
 * </pre>
 *
 * <p>A infraestrutura apenas carrega a configuração persistida.
 * A interpretação de hard cooldown e preferred cooldown continua
 * pertencendo ao domínio.</p>
 */
public final class JdbcPublicationSelectionProfileProvider
    implements PublicationSelectionProfileProvider {

    private final Connection connection;

    public JdbcPublicationSelectionProfileProvider(
        Connection connection
    ) {

        this.connection =
            Objects.requireNonNull(
                connection,
                "connection must not be null"
            );
    }

    @Override
    public PublicationSelectionProfile activeProfile(
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
                hard_cooldown_seconds,
                preferred_cooldown_seconds
            FROM publication_selection_profile
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
                        "No active publication selection profile "
                            + "was found for channel "
                            + validatedChannel
                            + " and destination "
                            + validatedDestination
                    );
                }

                PublicationSelectionProfile profile =
                    mapProfile(
                        resultSet
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one active publication selection "
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
                "Failed to load active publication selection profile",
                exception
            );
        }
    }

    private PublicationSelectionProfile mapProfile(
        ResultSet resultSet
    ) throws SQLException {

        return new PublicationSelectionProfile(
            resultSet.getString(
                "version"
            ),
            Duration.ofSeconds(
                resultSet.getLong(
                    "hard_cooldown_seconds"
                )
            ),
            Duration.ofSeconds(
                resultSet.getLong(
                    "preferred_cooldown_seconds"
                )
            )
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
