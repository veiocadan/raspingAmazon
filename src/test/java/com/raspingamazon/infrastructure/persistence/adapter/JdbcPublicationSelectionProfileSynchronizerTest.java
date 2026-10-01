package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationSelectionProfileSynchronizerTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final AtomicInteger SEQUENCE =
        new AtomicInteger();

    @Test
    void shouldCreateNewVersionAndPreserveHistoricalProfile()
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
                    uniqueDestination();

                insertProfile(
                    connection,
                    destination,
                    "SELECTION_V1",
                    172800L,
                    604800L,
                    true
                );

                JdbcPublicationSelectionProfileSynchronizer synchronizer =
                    new JdbcPublicationSelectionProfileSynchronizer(
                        connection
                    );

                synchronizer.synchronize(
                    CHANNEL,
                    destination,
                    profile(
                        "SELECTION_V2",
                        Duration.ofDays(
                            1
                        ),
                        Duration.ofDays(
                            5
                        )
                    )
                );

                assertEquals(
                    2,
                    countProfiles(
                        connection,
                        destination
                    )
                );

                assertFalse(
                    activeForVersion(
                        connection,
                        destination,
                        "SELECTION_V1"
                    )
                );

                assertTrue(
                    activeForVersion(
                        connection,
                        destination,
                        "SELECTION_V2"
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldBeIdempotentWhenDesiredVersionIsAlreadyActive()
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
                    uniqueDestination();

                insertProfile(
                    connection,
                    destination,
                    "SELECTION_V2",
                    86400L,
                    432000L,
                    true
                );

                JdbcPublicationSelectionProfileSynchronizer synchronizer =
                    new JdbcPublicationSelectionProfileSynchronizer(
                        connection
                    );

                PublicationSelectionProfile desired =
                    profile(
                        "SELECTION_V2",
                        Duration.ofDays(
                            1
                        ),
                        Duration.ofDays(
                            5
                        )
                    );

                synchronizer.synchronize(
                    CHANNEL,
                    destination,
                    desired
                );

                synchronizer.synchronize(
                    CHANNEL,
                    destination,
                    desired
                );

                assertEquals(
                    1,
                    countProfiles(
                        connection,
                        destination
                    )
                );

                assertTrue(
                    activeForVersion(
                        connection,
                        destination,
                        "SELECTION_V2"
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectReactivationOfHistoricalVersion()
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
                    uniqueDestination();

                insertProfile(
                    connection,
                    destination,
                    "SELECTION_V1",
                    172800L,
                    604800L,
                    false
                );

                insertProfile(
                    connection,
                    destination,
                    "SELECTION_V2",
                    86400L,
                    432000L,
                    true
                );

                JdbcPublicationSelectionProfileSynchronizer synchronizer =
                    new JdbcPublicationSelectionProfileSynchronizer(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        synchronizer.synchronize(
                            CHANNEL,
                            destination,
                            profile(
                                "SELECTION_V1",
                                Duration.ofDays(
                                    2
                                ),
                                Duration.ofDays(
                                    7
                                )
                            )
                        )
                );

                assertFalse(
                    activeForVersion(
                        connection,
                        destination,
                        "SELECTION_V1"
                    )
                );

                assertTrue(
                    activeForVersion(
                        connection,
                        destination,
                        "SELECTION_V2"
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldAllowOldValuesUnderNewVersion()
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
                    uniqueDestination();

                insertProfile(
                    connection,
                    destination,
                    "SELECTION_V1",
                    172800L,
                    604800L,
                    false
                );

                insertProfile(
                    connection,
                    destination,
                    "SELECTION_V2",
                    86400L,
                    432000L,
                    true
                );

                JdbcPublicationSelectionProfileSynchronizer synchronizer =
                    new JdbcPublicationSelectionProfileSynchronizer(
                        connection
                    );

                synchronizer.synchronize(
                    CHANNEL,
                    destination,
                    profile(
                        "SELECTION_V3",
                        Duration.ofDays(
                            2
                        ),
                        Duration.ofDays(
                            7
                        )
                    )
                );

                assertEquals(
                    3,
                    countProfiles(
                        connection,
                        destination
                    )
                );

                assertFalse(
                    activeForVersion(
                        connection,
                        destination,
                        "SELECTION_V1"
                    )
                );

                assertFalse(
                    activeForVersion(
                        connection,
                        destination,
                        "SELECTION_V2"
                    )
                );

                assertTrue(
                    activeForVersion(
                        connection,
                        destination,
                        "SELECTION_V3"
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectChangingExistingVersionValues()
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
                    uniqueDestination();

                insertProfile(
                    connection,
                    destination,
                    "SELECTION_V1",
                    172800L,
                    604800L,
                    true
                );

                JdbcPublicationSelectionProfileSynchronizer synchronizer =
                    new JdbcPublicationSelectionProfileSynchronizer(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        synchronizer.synchronize(
                            CHANNEL,
                            destination,
                            profile(
                                "SELECTION_V1",
                                Duration.ofDays(
                                    1
                                ),
                                Duration.ofDays(
                                    7
                                )
                            )
                        )
                );

                assertEquals(
                    172800L,
                    hardCooldownForVersion(
                        connection,
                        destination,
                        "SELECTION_V1"
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private PublicationSelectionProfile profile(
        String version,
        Duration hardCooldown,
        Duration preferredCooldown
    ) {

        return new PublicationSelectionProfile(
            version,
            hardCooldown,
            preferredCooldown
        );
    }

    private void insertProfile(
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

    private int countProfiles(
        Connection connection,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM publication_selection_profile
            WHERE channel = ?
              AND destination = ?
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

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                resultSet.next();

                return resultSet.getInt(
                    1
                );
            }
        }
    }

    private boolean activeForVersion(
        Connection connection,
        String destination,
        String version
    ) throws Exception {

        String sql =
            """
            SELECT active
            FROM publication_selection_profile
            WHERE channel = ?
              AND destination = ?
              AND version = ?
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

    private long hardCooldownForVersion(
        Connection connection,
        String destination,
        String version
    ) throws Exception {

        String sql =
            """
            SELECT hard_cooldown_seconds
            FROM publication_selection_profile
            WHERE channel = ?
              AND destination = ?
              AND version = ?
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

            try (ResultSet resultSet =
                     statement.executeQuery()) {

                assertTrue(
                    resultSet.next()
                );

                return resultSet.getLong(
                    "hard_cooldown_seconds"
                );
            }
        }
    }

    private String uniqueDestination() {

        return "@selection-env-"
            + SEQUENCE.incrementAndGet()
            + "-"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }
}
