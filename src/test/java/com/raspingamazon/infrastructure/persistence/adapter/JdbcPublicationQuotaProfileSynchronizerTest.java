package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationQuotaProfileSynchronizerTest {

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
                    "QUOTA_V1",
                    5,
                    "America/Sao_Paulo",
                    true
                );

                JdbcPublicationQuotaProfileSynchronizer synchronizer =
                    new JdbcPublicationQuotaProfileSynchronizer(
                        connection
                    );

                PublicationQuotaProfile desired =
                    new PublicationQuotaProfile(
                        "QUOTA_V2",
                        7,
                        ZoneId.of(
                            "America/Sao_Paulo"
                        )
                    );

                PublicationQuotaProfile result =
                    synchronizer.synchronize(
                        CHANNEL,
                        destination,
                        desired
                    );

                assertEquals(
                    desired,
                    result
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
                        "QUOTA_V1"
                    )
                );

                assertTrue(
                    activeForVersion(
                        connection,
                        destination,
                        "QUOTA_V2"
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
                    "QUOTA_V2",
                    7,
                    "America/Sao_Paulo",
                    true
                );

                JdbcPublicationQuotaProfileSynchronizer synchronizer =
                    new JdbcPublicationQuotaProfileSynchronizer(
                        connection
                    );

                PublicationQuotaProfile desired =
                    new PublicationQuotaProfile(
                        "QUOTA_V2",
                        7,
                        ZoneId.of(
                            "America/Sao_Paulo"
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
                        "QUOTA_V2"
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
                    "QUOTA_V1",
                    5,
                    "America/Sao_Paulo",
                    false
                );

                insertProfile(
                    connection,
                    destination,
                    "QUOTA_V2",
                    7,
                    "America/Sao_Paulo",
                    true
                );

                JdbcPublicationQuotaProfileSynchronizer synchronizer =
                    new JdbcPublicationQuotaProfileSynchronizer(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        synchronizer.synchronize(
                            CHANNEL,
                            destination,
                            new PublicationQuotaProfile(
                                "QUOTA_V1",
                                5,
                                ZoneId.of(
                                    "America/Sao_Paulo"
                                )
                            )
                        )
                );

                /*
                 * A versão histórica continua histórica.
                 */
                assertFalse(
                    activeForVersion(
                        connection,
                        destination,
                        "QUOTA_V1"
                    )
                );

                /*
                 * A configuração atual não foi afetada.
                 */
                assertTrue(
                    activeForVersion(
                        connection,
                        destination,
                        "QUOTA_V2"
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
                    "QUOTA_V1",
                    5,
                    "America/Sao_Paulo",
                    false
                );

                insertProfile(
                    connection,
                    destination,
                    "QUOTA_V2",
                    7,
                    "America/Sao_Paulo",
                    true
                );

                JdbcPublicationQuotaProfileSynchronizer synchronizer =
                    new JdbcPublicationQuotaProfileSynchronizer(
                        connection
                    );

                /*
                 * Voltamos para quota 5, mas não para QUOTA_V1.
                 */
                synchronizer.synchronize(
                    CHANNEL,
                    destination,
                    new PublicationQuotaProfile(
                        "QUOTA_V3",
                        5,
                        ZoneId.of(
                            "America/Sao_Paulo"
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
                        "QUOTA_V1"
                    )
                );

                assertFalse(
                    activeForVersion(
                        connection,
                        destination,
                        "QUOTA_V2"
                    )
                );

                assertTrue(
                    activeForVersion(
                        connection,
                        destination,
                        "QUOTA_V3"
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
                    "QUOTA_V1",
                    5,
                    "America/Sao_Paulo",
                    true
                );

                JdbcPublicationQuotaProfileSynchronizer synchronizer =
                    new JdbcPublicationQuotaProfileSynchronizer(
                        connection
                    );

                assertThrows(
                    IllegalStateException.class,
                    () ->
                        synchronizer.synchronize(
                            CHANNEL,
                            destination,
                            new PublicationQuotaProfile(
                                "QUOTA_V1",
                                10,
                                ZoneId.of(
                                    "America/Sao_Paulo"
                                )
                            )
                        )
                );

                assertEquals(
                    5,
                    maximumForVersion(
                        connection,
                        destination,
                        "QUOTA_V1"
                    )
                );

                assertTrue(
                    activeForVersion(
                        connection,
                        destination,
                        "QUOTA_V1"
                    )
                );

            } finally {

                connection.rollback();
            }
        }
    }

    private void insertProfile(
        Connection connection,
        String destination,
        String version,
        int maximum,
        String zone,
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
                zone
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
            FROM publication_quota_profile
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
            FROM publication_quota_profile
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

    private int maximumForVersion(
        Connection connection,
        String destination,
        String version
    ) throws Exception {

        String sql =
            """
            SELECT max_publications_per_day
            FROM publication_quota_profile
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

                return resultSet.getInt(
                    "max_publications_per_day"
                );
            }
        }
    }

    private String uniqueDestination() {

        return "@quota_env_"
            + SEQUENCE.incrementAndGet()
            + "_"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }
}
