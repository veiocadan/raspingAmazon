package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.testsupport.database.PostgresIntegrationTest;

import com.raspingamazon.domain.publication.selection.PublicationSelectionPolicy;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PostgresIntegrationTest
class JdbcPublicationSelectionProfileProviderTest {

    private static final String CHANNEL =
        "TELEGRAM";

    @Test
    void shouldLoadActiveProfileForExactScope()
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
                    "PUBLICATION_SELECTION_OLD",
                    Duration.ofDays(
                        1
                    ),
                    Duration.ofDays(
                        3
                    ),
                    false
                );

                insertProfile(
                    connection,
                    CHANNEL,
                    destination,
                    PublicationSelectionPolicy.VERSION,
                    Duration.ofDays(
                        2
                    ),
                    Duration.ofDays(
                        7
                    ),
                    true
                );

                insertProfile(
                    connection,
                    CHANNEL,
                    destination + "-other",
                    "PUBLICATION_SELECTION_OTHER",
                    Duration.ofDays(
                        5
                    ),
                    Duration.ofDays(
                        10
                    ),
                    true
                );

                PublicationSelectionProfile profile =
                    new JdbcPublicationSelectionProfileProvider(
                        connection
                    ).activeProfile(
                        CHANNEL,
                        destination
                    );

                assertEquals(
                    PublicationSelectionPolicy.VERSION,
                    profile.version()
                );

                assertEquals(
                    Duration.ofDays(
                        2
                    ),
                    profile.hardCooldown()
                );

                assertEquals(
                    Duration.ofDays(
                        7
                    ),
                    profile.preferredCooldown()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldFailWhenScopeHasNoActiveProfile()
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
                    "PUBLICATION_SELECTION_INACTIVE",
                    Duration.ofDays(
                        2
                    ),
                    Duration.ofDays(
                        7
                    ),
                    false
                );

                JdbcPublicationSelectionProfileProvider provider =
                    new JdbcPublicationSelectionProfileProvider(
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

            JdbcPublicationSelectionProfileProvider provider =
                new JdbcPublicationSelectionProfileProvider(
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
                        "   ",
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

        return "@selection-profile-"
            + suffix
            + "-"
            + System.nanoTime();
    }

    private void insertProfile(
        Connection connection,
        String channel,
        String destination,
        String version,
        Duration hardCooldown,
        Duration preferredCooldown,
        boolean active
    ) throws Exception {

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

            statement.setLong(
                4,
                hardCooldown.getSeconds()
            );

            statement.setLong(
                5,
                preferredCooldown.getSeconds()
            );

            statement.setBoolean(
                6,
                active
            );

            statement.executeUpdate();
        }
    }
}
