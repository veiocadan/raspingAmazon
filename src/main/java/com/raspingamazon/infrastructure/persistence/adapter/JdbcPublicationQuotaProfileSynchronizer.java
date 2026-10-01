package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Sincroniza no PostgreSQL o estado desejado da configuração
 * operacional de quota.
 *
 * <p>Regra de versionamento:</p>
 *
 * <pre>
 * versão inexistente
 *     -> cria nova versão
 *     -> desativa versão anterior
 *     -> nova versão fica ativa
 *
 * mesma versão já ativa + mesmos valores
 *     -> operação idempotente
 *
 * mesma versão já ativa + valores diferentes
 *     -> erro
 *
 * versão histórica/inativa
 *     -> erro
 *     -> nunca é reativada
 * </pre>
 *
 * <p>Portanto, retornar a valores antigos exige sempre uma nova
 * identidade semântica:</p>
 *
 * <pre>
 * QUOTA_V1 = 5
 * QUOTA_V2 = 7
 * QUOTA_V3 = 10
 * QUOTA_V4 = 5
 * </pre>
 *
 * <p>Essa política preserva integralmente o histórico operacional.</p>
 */
public final class JdbcPublicationQuotaProfileSynchronizer {

    private final Connection connection;

    private final JdbcTransactionAdapter transactionAdapter;

    public JdbcPublicationQuotaProfileSynchronizer(
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

    public PublicationQuotaProfile synchronize(
        String channel,
        String destination,
        PublicationQuotaProfile desiredProfile
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

    private PublicationQuotaProfile synchronizeInsideTransaction(
        String channel,
        String destination,
        PublicationQuotaProfile desiredProfile
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

                    /*
                     * Reinicialização normal da aplicação com a
                     * mesma configuração.
                     */
                    return desiredProfile;
                }

                /*
                 * Uma versão que já foi histórica não volta a ser
                 * ativa.
                 *
                 * Mesmo que seus valores sejam exatamente os
                 * desejados novamente, deve ser criada Vn+1.
                 */
                throw new IllegalStateException(
                    "Publication quota version "
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
                "Failed to synchronize publication quota profile",
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
                max_publications_per_day,
                quota_zone,
                active
            FROM publication_quota_profile
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
                        resultSet.getInt(
                            "max_publications_per_day"
                        ),
                        resultSet.getString(
                            "quota_zone"
                        ),
                        resultSet.getBoolean(
                            "active"
                        )
                    );

                if (resultSet.next()) {

                    throw new IllegalStateException(
                        "More than one publication quota profile "
                            + "exists for the same scope and version"
                    );
                }

                return stored;
            }
        }
    }

    private void validateImmutableVersion(
        PublicationQuotaProfile desired,
        StoredProfile stored
    ) {

        boolean sameMaximum =
            desired.maxPublicationsPerDay()
                == stored.maxPublicationsPerDay();

        boolean sameZone =
            desired.quotaZone()
                .getId()
                .equals(
                    stored.quotaZone()
                );

        if (!sameMaximum
            || !sameZone) {

            throw new IllegalStateException(
                "Publication quota version "
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
            UPDATE publication_quota_profile
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
        PublicationQuotaProfile profile
    ) throws SQLException {

        String sql =
            """
            INSERT INTO publication_quota_profile (
                channel,
                destination,
                version,
                max_publications_per_day,
                quota_zone,
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

            statement.setInt(
                4,
                profile.maxPublicationsPerDay()
            );

            statement.setString(
                5,
                profile.quotaZone()
                    .getId()
            );

            int inserted =
                statement.executeUpdate();

            if (inserted != 1) {

                throw new IllegalStateException(
                    "Publication quota profile insert "
                        + "affected an unexpected number of rows: "
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
        int maxPublicationsPerDay,
        String quotaZone,
        boolean active
    ) {

        private StoredProfile {

            Objects.requireNonNull(
                quotaZone,
                "quotaZone must not be null"
            );

            /*
             * Também valida que o dado persistido continua sendo
             * uma ZoneId semanticamente válida.
             */
            ZoneId.of(
                quotaZone
            );
        }
    }
}
