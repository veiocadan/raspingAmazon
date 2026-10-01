package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.config.PublicationAutomationPolicyConfig;
import com.raspingamazon.infrastructure.config.PublicationOperationalPolicyConfig;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationAutomationPolicySynchronizerTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final ZoneId ZONE =
        ZoneId.of(
            "America/Sao_Paulo"
        );

    private static final AtomicInteger SEQUENCE =
        new AtomicInteger();

    @Test
    void shouldSynchronizeSelectionQuotaAndCadenceTogether()
        throws Exception {

        ApplicationConfig applicationConfig =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     applicationConfig
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                String destination =
                    uniqueDestination(
                        "success"
                    );

                PublicationAutomationPolicyConfig desired =
                    configuration(
                        destination,
                        "PUBLICATION_SELECTION_V1_CONFIG_V1",
                        Duration.ofDays(
                            2
                        ),
                        Duration.ofDays(
                            7
                        ),
                        "QUOTA_V1",
                        7,
                        "CADENCE_V1",
                        Duration.ofHours(
                            2
                        )
                    );

                JdbcPublicationAutomationPolicySynchronizer
                    synchronizer =
                    new JdbcPublicationAutomationPolicySynchronizer(
                        connection
                    );

                PublicationAutomationPolicyConfig result =
                    synchronizer.synchronize(
                        desired
                    );

                assertEquals(
                    desired,
                    result
                );

                assertEquals(
                    "PUBLICATION_SELECTION_V1_CONFIG_V1",
                    activeVersion(
                        connection,
                        "publication_selection_profile",
                        destination
                    )
                );

                assertEquals(
                    "QUOTA_V1",
                    activeVersion(
                        connection,
                        "publication_quota_profile",
                        destination
                    )
                );

                assertEquals(
                    "CADENCE_V1",
                    activeVersion(
                        connection,
                        "publication_cadence_profile",
                        destination
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldBeIdempotentWhenAllDesiredVersionsAreAlreadyActive()
        throws Exception {

        ApplicationConfig applicationConfig =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     applicationConfig
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                String destination =
                    uniqueDestination(
                        "idempotent"
                    );

                PublicationAutomationPolicyConfig desired =
                    configuration(
                        destination,
                        "PUBLICATION_SELECTION_V1_CONFIG_V1",
                        Duration.ofDays(
                            2
                        ),
                        Duration.ofDays(
                            7
                        ),
                        "QUOTA_V1",
                        7,
                        "CADENCE_V1",
                        Duration.ofHours(
                            2
                        )
                    );

                JdbcPublicationAutomationPolicySynchronizer
                    synchronizer =
                    new JdbcPublicationAutomationPolicySynchronizer(
                        connection
                    );

                synchronizer.synchronize(
                    desired
                );

                synchronizer.synchronize(
                    desired
                );

                assertEquals(
                    1,
                    countProfiles(
                        connection,
                        "publication_selection_profile",
                        destination
                    )
                );

                assertEquals(
                    1,
                    countProfiles(
                        connection,
                        "publication_quota_profile",
                        destination
                    )
                );

                assertEquals(
                    1,
                    countProfiles(
                        connection,
                        "publication_cadence_profile",
                        destination
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRollbackSelectionAndQuotaWhenCadenceFails()
        throws Exception {

        ApplicationConfig applicationConfig =
            EnvironmentConfigProvider.load();

        try (Connection connection =
                 DatabaseConnection.open(
                     applicationConfig
                 )) {

            connection.setAutoCommit(
                false
            );

            try {

                String destination =
                    uniqueDestination(
                        "rollback"
                    );

                /*
                 * Estado operacional atual.
                 */
                insertSelectionProfile(
                    connection,
                    destination,
                    "PUBLICATION_SELECTION_V1_CONFIG_V1",
                    172800L,
                    604800L,
                    true
                );

                insertQuotaProfile(
                    connection,
                    destination,
                    "QUOTA_V1",
                    5,
                    true
                );

                /*
                 * CADENCE_V1 já existiu no passado.
                 */
                insertCadenceProfile(
                    connection,
                    destination,
                    "CADENCE_V1",
                    7200L,
                    false
                );

                /*
                 * CADENCE_V2 é a política atualmente ativa.
                 */
                insertCadenceProfile(
                    connection,
                    destination,
                    "CADENCE_V2",
                    3600L,
                    true
                );

                PublicationAutomationPolicyConfig desired =
                    configuration(
                        destination,
                        "PUBLICATION_SELECTION_V1_CONFIG_V2",
                        Duration.ofDays(
                            1
                        ),
                        Duration.ofDays(
                            5
                        ),
                        "QUOTA_V2",
                        7,

                        /*
                         * Tentativa proibida de reativar
                         * CADENCE_V1.
                         */
                        "CADENCE_V1",
                        Duration.ofHours(
                            2
                        )
                    );

                JdbcPublicationAutomationPolicySynchronizer
                    synchronizer =
                    new JdbcPublicationAutomationPolicySynchronizer(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        synchronizer.synchronize(
                            desired
                        )
                );

                /*
                 * Selection voltou integralmente ao estado anterior.
                 */
                assertEquals(
                    "PUBLICATION_SELECTION_V1_CONFIG_V1",
                    activeVersion(
                        connection,
                        "publication_selection_profile",
                        destination
                    )
                );

                assertFalse(
                    versionExists(
                        connection,
                        "publication_selection_profile",
                        destination,
                        "PUBLICATION_SELECTION_V1_CONFIG_V2"
                    )
                );

                /*
                 * Quota também foi revertida.
                 */
                assertEquals(
                    "QUOTA_V1",
                    activeVersion(
                        connection,
                        "publication_quota_profile",
                        destination
                    )
                );

                assertFalse(
                    versionExists(
                        connection,
                        "publication_quota_profile",
                        destination,
                        "QUOTA_V2"
                    )
                );

                /*
                 * Cadência permanece exatamente como estava.
                 */
                assertFalse(
                    activeForVersion(
                        connection,
                        "publication_cadence_profile",
                        destination,
                        "CADENCE_V1"
                    )
                );

                assertTrue(
                    activeForVersion(
                        connection,
                        "publication_cadence_profile",
                        destination,
                        "CADENCE_V2"
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private PublicationAutomationPolicyConfig configuration(
        String destination,
        String selectionVersion,
        Duration hardCooldown,
        Duration preferredCooldown,
        String quotaVersion,
        int maximum,
        String cadenceVersion,
        Duration interval
    ) {

        PublicationOperationalPolicyConfig operationalPolicy =
            new PublicationOperationalPolicyConfig(
                CHANNEL,
                destination,
                new PublicationQuotaProfile(
                    quotaVersion,
                    maximum,
                    ZONE
                ),
                new PublicationCadenceProfile(
                    cadenceVersion,
                    interval,
                    LocalTime.of(
                        8,
                        0
                    ),
                    LocalTime.of(
                        22,
                        0
                    ),
                    ZONE
                )
            );

        PublicationSelectionProfile selectionProfile =
            new PublicationSelectionProfile(
                selectionVersion,
                hardCooldown,
                preferredCooldown
            );

        return new PublicationAutomationPolicyConfig(
            operationalPolicy,
            selectionProfile
        );
    }

    private void insertSelectionProfile(
        Connection connection,
        String destination,
        String version,
        long hardCooldownSeconds,
        long preferredCooldownSeconds,
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
                CHANNEL
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
                hardCooldownSeconds
            );

            statement.setLong(
                5,
                preferredCooldownSeconds
            );

            statement.setBoolean(
                6,
                active
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private void insertQuotaProfile(
        Connection connection,
        String destination,
        String version,
        int maximum,
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
                CHANNEL
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
                ZONE.getId()
            );

            statement.setBoolean(
                6,
                active
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private void insertCadenceProfile(
        Connection connection,
        String destination,
        String version,
        long intervalSeconds,
        boolean active
    ) throws Exception {

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
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                CHANNEL
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
                intervalSeconds
            );

            statement.setObject(
                5,
                LocalTime.of(
                    8,
                    0
                )
            );

            statement.setObject(
                6,
                LocalTime.of(
                    22,
                    0
                )
            );

            statement.setString(
                7,
                ZONE.getId()
            );

            statement.setBoolean(
                8,
                active
            );

            assertEquals(
                1,
                statement.executeUpdate()
            );
        }
    }

    private String activeVersion(
        Connection connection,
        String table,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT version
            FROM %s
            WHERE channel = ?
              AND destination = ?
              AND active = true
            """
                .formatted(
                    table
                );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                CHANNEL
            );

            statement.setString(
                2,
                destination
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                String version =
                    resultSet.getString(
                        "version"
                    );

                assertFalse(
                    resultSet.next()
                );

                return version;
            }
        }
    }

    private boolean activeForVersion(
        Connection connection,
        String table,
        String destination,
        String version
    ) throws Exception {

        String sql =
            """
            SELECT active
            FROM %s
            WHERE channel = ?
              AND destination = ?
              AND version = ?
            """
                .formatted(
                    table
                );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                CHANNEL
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

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getBoolean(
                    "active"
                );
            }
        }
    }

    private boolean versionExists(
        Connection connection,
        String table,
        String destination,
        String version
    ) throws Exception {

        String sql =
            """
            SELECT 1
            FROM %s
            WHERE channel = ?
              AND destination = ?
              AND version = ?
            """
                .formatted(
                    table
                );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                CHANNEL
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

                return resultSet.next();
            }
        }
    }

    private int countProfiles(
        Connection connection,
        String table,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM %s
            WHERE channel = ?
              AND destination = ?
            """
                .formatted(
                    table
                );

        try (PreparedStatement statement =
                 connection.prepareStatement(
                     sql
                 )) {

            statement.setString(
                1,
                CHANNEL
            );

            statement.setString(
                2,
                destination
            );

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getInt(
                    1
                );
            }
        }
    }

    private String uniqueDestination(
        String suffix
    ) {

        return "@automation-policy-"
            + suffix
            + "-"
            + SEQUENCE.incrementAndGet()
            + "-"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }
}
