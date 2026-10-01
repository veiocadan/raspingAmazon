package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Sincroniza o perfil temporal desejado de seleção operacional.
 *
 * <p>Política de versionamento:</p>
 *
 * <pre>
 * versão inexistente
 *     -> cria nova versão ativa
 *     -> versão anteriormente ativa vira histórica
 *
 * mesma versão ativa + mesmos valores
 *     -> idempotente
 *
 * mesma versão ativa + valores diferentes
 *     -> erro
 *
 * versão histórica
 *     -> nunca é reativada
 *     -> criar Vn+1
 * </pre>
 */
public final class JdbcPublicationSelectionProfileSynchronizer {

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    public JdbcPublicationSelectionProfileSynchronizer(
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

    public PublicationSelectionProfile synchronize(
        String channel,
        String destination,
        PublicationSelectionProfile desiredProfile
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

        validatePersistableProfile(
            desiredProfile
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

    private PublicationSelectionProfile synchronizeInsideTransaction(
        String channel,
        String destination,
        PublicationSelectionProfile desiredProfile
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
                    "Publication selection version "
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
                "Failed to synchronize publication selection profile",
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
                hard_cooldown_seconds,
                preferred_cooldown_seconds,
                active
            FROM publication_selection_profile
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
                            "hard_cooldown_seconds"
                        ),
                        resultSet.getLong(
                            "preferred_cooldown_seconds"
                        ),
                        resultSet.getBoolean(
                            "active"
                        )
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one publication selection profile "
                            + "exists for the same scope and version"
                    );
                }

                return stored;
            }
        }
    }

    private void validateImmutableVersion(
        PublicationSelectionProfile desired,
        StoredProfile stored
    ) {

        boolean sameHardCooldown =
            desired.hardCooldown()
                .toSeconds()
                == stored.hardCooldownSeconds();

        boolean samePreferredCooldown =
            desired.preferredCooldown()
                .toSeconds()
                == stored.preferredCooldownSeconds();

        if (!sameHardCooldown
            || !samePreferredCooldown) {

            throw new IllegalStateException(
                "Publication selection version "
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
            UPDATE publication_selection_profile
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
        PublicationSelectionProfile profile
    ) throws SQLException {

        String sql =
            """
            INSERT INTO publication_selection_profile (
                channel,
                destination,
                version,
                hard_cooldown_seconds,
                preferred_cooldown_seconds,
                active
            )
            VALUES (
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
                profile.hardCooldown()
                    .toSeconds()
            );

            statement.setLong(
                5,
                profile.preferredCooldown()
                    .toSeconds()
            );

            int inserted =
                statement.executeUpdate();

            if (inserted != 1) {

                throw new IllegalStateException(
                    "Publication selection profile insert affected "
                        + "an unexpected number of rows: "
                        + inserted
                );
            }
        }
    }

    private void validatePersistableProfile(
        PublicationSelectionProfile profile
    ) {

        if (profile.hardCooldown()
            .getNano() != 0
            || profile.preferredCooldown()
            .getNano() != 0) {

            throw new IllegalArgumentException(
                "Publication selection cooldowns must use whole seconds"
            );
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
        long hardCooldownSeconds,
        long preferredCooldownSeconds,
        boolean active
    ) {

        private StoredProfile {

            if (hardCooldownSeconds < 0L) {

                throw new IllegalStateException(
                    "Persisted hard cooldown must not be negative"
                );
            }

            if (preferredCooldownSeconds
                < hardCooldownSeconds) {

                throw new IllegalStateException(
                    "Persisted preferred cooldown must be greater "
                        + "than or equal to hard cooldown"
                );
            }
        }
    }
}
