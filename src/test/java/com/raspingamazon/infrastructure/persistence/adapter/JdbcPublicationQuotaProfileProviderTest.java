package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PostgresIntegrationTest
class JdbcPublicationQuotaProfileProviderTest {

    private static final String CHANNEL =
        "TELEGRAM";

    @Test
    void shouldLoadActiveQuotaProfileForExactScope()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                String destination =
                    uniqueDestination(
                        "active"
                    );

                insertProfile(
                    connection,
                    CHANNEL,
                    destination,
                    "PUBLICATION_QUOTA_OLD",
                    3,
                    "UTC",
                    false
                );

                insertProfile(
                    connection,
                    CHANNEL,
                    destination,
                    "PUBLICATION_QUOTA_V1",
                    7,
                    "America/Sao_Paulo",
                    true
                );

                insertProfile(
                    connection,
                    CHANNEL,
                    destination + "-other",
                    "PUBLICATION_QUOTA_OTHER",
                    20,
                    "UTC",
                    true
                );

                PublicationQuotaProfile profile =
                    new JdbcPublicationQuotaProfileProvider(
                        connection
                    ).activeProfile(
                        CHANNEL,
                        destination
                    );

                assertEquals(
                    "PUBLICATION_QUOTA_V1",
                    profile.version()
                );

                assertEquals(
                    7,
                    profile.maxPublicationsPerDay()
                );

                assertEquals(
                    ZoneId.of(
                        "America/Sao_Paulo"
                    ),
                    profile.quotaZone()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldFailWhenScopeHasNoActiveQuotaProfile()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                String destination =
                    uniqueDestination(
                        "missing"
                    );

                insertProfile(
                    connection,
                    CHANNEL,
                    destination,
                    "PUBLICATION_QUOTA_INACTIVE",
                    7,
                    "America/Sao_Paulo",
                    false
                );

                JdbcPublicationQuotaProfileProvider provider =
                    new JdbcPublicationQuotaProfileProvider(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        provider.activeProfile(
                            CHANNEL,
                            destination
                        )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectInvalidScopeInput()
        throws Exception {

        ApplicationConfig config =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     config
                 )) {

            JdbcPublicationQuotaProfileProvider provider =
                new JdbcPublicationQuotaProfileProvider(
                    connection
                );

            assertThrows(
                NullPointerException.class,
                () ->
                    provider.activeProfile(
                        null,
                        "@phase18"
                    )
            );

            assertThrows(
                IllegalArgumentException.class,
                () ->
                    provider.activeProfile(
                        "",
                        "@phase18"
                    )
            );

            assertThrows(
                NullPointerException.class,
                () ->
                    provider.activeProfile(
                        CHANNEL,
                        null
                    )
            );

            assertThrows(
                IllegalArgumentException.class,
                () ->
                    provider.activeProfile(
                        CHANNEL,
                        "   "
                    )
            );
        }
    }

    private String uniqueDestination(
        String suffix
    ) {

        return "@quota-profile-"
            + suffix
            + "-"
            + System.nanoTime();
    }

    private void insertProfile(
        Connection connection,
        String channel,
        String destination,
        String version,
        int maximum,
        String quotaZone,
        boolean active
    ) throws Exception {

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
            VALUES (?, ?, ?, ?, ?, ?)
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

            statement.setInt(
                4,
                maximum
            );

            statement.setString(
                5,
                quotaZone
            );

            statement.setBoolean(
                6,
                active
            );

            statement.executeUpdate();
        }
    }
}
