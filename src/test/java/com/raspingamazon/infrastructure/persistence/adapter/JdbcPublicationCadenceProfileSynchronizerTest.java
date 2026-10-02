package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
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
class JdbcPublicationCadenceProfileSynchronizerTest {

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
                    "CADENCE_V1",
                    7200L,
                    LocalTime.of(
                        8,
                        0
                    ),
                    LocalTime.of(
                        22,
                        0
                    ),
                    "America/Sao_Paulo",
                    true
                );

                JdbcPublicationCadenceProfileSynchronizer synchronizer =
                    new JdbcPublicationCadenceProfileSynchronizer(
                        connection
                    );

                PublicationCadenceProfile desired =
                    profile(
                        "CADENCE_V2",
                        Duration.ofHours(
                            1
                        )
                    );

                synchronizer.synchronize(
                    CHANNEL,
                    destination,
                    desired
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
                        "CADENCE_V1"
                    )
                );

                assertTrue(
                    activeForVersion(
                        connection,
                        destination,
                        "CADENCE_V2"
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
                    "CADENCE_V2",
                    3600L,
                    LocalTime.of(
                        8,
                        0
                    ),
                    LocalTime.of(
                        22,
                        0
                    ),
                    "America/Sao_Paulo",
                    true
                );

                JdbcPublicationCadenceProfileSynchronizer synchronizer =
                    new JdbcPublicationCadenceProfileSynchronizer(
                        connection
                    );

                PublicationCadenceProfile desired =
                    profile(
                        "CADENCE_V2",
                        Duration.ofHours(
                            1
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
                    "CADENCE_V1",
                    7200L,
                    LocalTime.of(
                        8,
                        0
                    ),
                    LocalTime.of(
                        22,
                        0
                    ),
                    "America/Sao_Paulo",
                    false
                );

                insertProfile(
                    connection,
                    destination,
                    "CADENCE_V2",
                    3600L,
                    LocalTime.of(
                        8,
                        0
                    ),
                    LocalTime.of(
                        22,
                        0
                    ),
                    "America/Sao_Paulo",
                    true
                );

                JdbcPublicationCadenceProfileSynchronizer synchronizer =
                    new JdbcPublicationCadenceProfileSynchronizer(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        synchronizer.synchronize(
                            CHANNEL,
                            destination,
                            profile(
                                "CADENCE_V1",
                                Duration.ofHours(
                                    2
                                )
                            )
                        )
                );

                assertFalse(
                    activeForVersion(
                        connection,
                        destination,
                        "CADENCE_V1"
                    )
                );

                assertTrue(
                    activeForVersion(
                        connection,
                        destination,
                        "CADENCE_V2"
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
                    "CADENCE_V1",
                    7200L,
                    LocalTime.of(
                        8,
                        0
                    ),
                    LocalTime.of(
                        22,
                        0
                    ),
                    "America/Sao_Paulo",
                    false
                );

                insertProfile(
                    connection,
                    destination,
                    "CADENCE_V2",
                    3600L,
                    LocalTime.of(
                        8,
                        0
                    ),
                    LocalTime.of(
                        22,
                        0
                    ),
                    "America/Sao_Paulo",
                    true
                );

                JdbcPublicationCadenceProfileSynchronizer synchronizer =
                    new JdbcPublicationCadenceProfileSynchronizer(
                        connection
                    );

                /*
                 * Voltamos para PT2H, mas como CADENCE_V3.
                 */
                synchronizer.synchronize(
                    CHANNEL,
                    destination,
                    profile(
                        "CADENCE_V3",
                        Duration.ofHours(
                            2
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
                        "CADENCE_V1"
                    )
                );

                assertFalse(
                    activeForVersion(
                        connection,
                        destination,
                        "CADENCE_V2"
                    )
                );

                assertTrue(
                    activeForVersion(
                        connection,
                        destination,
                        "CADENCE_V3"
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void shouldRejectChangingValuesOfExistingVersion()
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
                    "CADENCE_V1",
                    7200L,
                    LocalTime.of(
                        8,
                        0
                    ),
                    LocalTime.of(
                        22,
                        0
                    ),
                    "America/Sao_Paulo",
                    true
                );

                JdbcPublicationCadenceProfileSynchronizer synchronizer =
                    new JdbcPublicationCadenceProfileSynchronizer(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        synchronizer.synchronize(
                            CHANNEL,
                            destination,
                            profile(
                                "CADENCE_V1",
                                Duration.ofHours(
                                    1
                                )
                            )
                        )
                );

                assertEquals(
                    7200L,
                    intervalForVersion(
                        connection,
                        destination,
                        "CADENCE_V1"
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    @Test
    void providerShouldLoadCurrentActiveProfile()
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
                    "CADENCE_V1",
                    7200L,
                    LocalTime.of(
                        8,
                        0
                    ),
                    LocalTime.of(
                        22,
                        0
                    ),
                    "America/Sao_Paulo",
                    false
                );

                insertProfile(
                    connection,
                    destination,
                    "CADENCE_V2",
                    5400L,
                    LocalTime.of(
                        9,
                        0
                    ),
                    LocalTime.of(
                        21,
                        0
                    ),
                    "America/Sao_Paulo",
                    true
                );

                JdbcPublicationCadenceProfileProvider provider =
                    new JdbcPublicationCadenceProfileProvider(
                        connection
                    );

                PublicationCadenceProfile profile =
                    provider.activeProfile(
                        CHANNEL,
                        destination
                    );

                assertEquals(
                    "CADENCE_V2",
                    profile.version()
                );

                assertEquals(
                    Duration.ofMinutes(
                        90
                    ),
                    profile.interval()
                );

                assertEquals(
                    LocalTime.of(
                        9,
                        0
                    ),
                    profile.windowStart()
                );

                assertEquals(
                    LocalTime.of(
                        21,
                        0
                    ),
                    profile.windowEnd()
                );

                assertEquals(
                    ZoneId.of(
                        "America/Sao_Paulo"
                    ),
                    profile.zone()
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private PublicationCadenceProfile profile(
        String version,
        Duration interval
    ) {

        return new PublicationCadenceProfile(
            version,
            interval,
            LocalTime.of(
                8,
                0
            ),
            LocalTime.of(
                22,
                0
            ),
            ZoneId.of(
                "America/Sao_Paulo"
            )
        );
    }

    private void insertProfile(
        Connection connection,
        String destination,
        String version,
        long intervalSeconds,
        LocalTime windowStart,
        LocalTime windowEnd,
        String zone,
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
                windowStart
            );

            statement.setObject(
                6,
                windowEnd
            );

            statement.setString(
                7,
                zone
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

    private int countProfiles(
        Connection connection,
        String destination
    ) throws Exception {

        String sql =
            """
            SELECT COUNT(*)
            FROM publication_cadence_profile
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
            FROM publication_cadence_profile
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

    private long intervalForVersion(
        Connection connection,
        String destination,
        String version
    ) throws Exception {

        String sql =
            """
            SELECT interval_seconds
            FROM publication_cadence_profile
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
                    "interval_seconds"
                );
            }
        }
    }

    private String uniqueDestination() {

        return "@cadence_env_"
            + SEQUENCE.incrementAndGet()
            + "_"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }
}
