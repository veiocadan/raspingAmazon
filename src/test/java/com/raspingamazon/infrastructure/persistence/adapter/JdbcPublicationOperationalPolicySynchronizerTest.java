package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@PostgresIntegrationTest
class JdbcPublicationOperationalPolicySynchronizerTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final AtomicInteger SEQUENCE =
        new AtomicInteger();

    @Test
    void shouldSynchronizeQuotaAndCadenceAtomically()
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
                    uniqueDestination();

                insertQuota(
                    connection,
                    destination,
                    "QUOTA_V1",
                    5,
                    true
                );

                insertCadence(
                    connection,
                    destination,
                    "CADENCE_V1",
                    7200L,
                    true
                );

                JdbcPublicationOperationalPolicySynchronizer
                    synchronizer =
                    new JdbcPublicationOperationalPolicySynchronizer(
                        connection
                    );

                synchronizer.synchronize(
                    configuration(
                        destination,
                        "QUOTA_V2",
                        7,
                        "CADENCE_V2",
                        Duration.ofHours(
                            1
                        )
                    )
                );

                assertFalse(
                    quotaActive(
                        connection,
                        destination,
                        "QUOTA_V1"
                    )
                );

                assertTrue(
                    quotaActive(
                        connection,
                        destination,
                        "QUOTA_V2"
                    )
                );

                assertFalse(
                    cadenceActive(
                        connection,
                        destination,
                        "CADENCE_V1"
                    )
                );

                assertTrue(
                    cadenceActive(
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
    void shouldRollbackQuotaWhenCadenceSynchronizationFails()
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
                    uniqueDestination();

                /*
                 * Quota atualmente ativa.
                 */
                insertQuota(
                    connection,
                    destination,
                    "QUOTA_V1",
                    5,
                    true
                );

                /*
                 * CADENCE_V1 já existiu e tornou-se histórica.
                 */
                insertCadence(
                    connection,
                    destination,
                    "CADENCE_V1",
                    7200L,
                    false
                );

                insertCadence(
                    connection,
                    destination,
                    "CADENCE_V2",
                    3600L,
                    true
                );

                JdbcPublicationOperationalPolicySynchronizer
                    synchronizer =
                    new JdbcPublicationOperationalPolicySynchronizer(
                        connection
                    );

                /*
                 * QUOTA_V2 seria válida.
                 *
                 * Porém CADENCE_V1 não pode ser reativada.
                 *
                 * A falha da cadência precisa desfazer também
                 * qualquer mudança da quota.
                 */
                assertThrows(
                    IllegalStateException.class,
                    () ->
                        synchronizer.synchronize(
                            configuration(
                                destination,
                                "QUOTA_V2",
                                7,
                                "CADENCE_V1",
                                Duration.ofHours(
                                    2
                                )
                            )
                        )
                );

                /*
                 * A quota original continua ativa.
                 */
                assertTrue(
                    quotaActive(
                        connection,
                        destination,
                        "QUOTA_V1"
                    )
                );

                /*
                 * QUOTA_V2 foi integralmente revertida.
                 */
                assertFalse(
                    quotaVersionExists(
                        connection,
                        destination,
                        "QUOTA_V2"
                    )
                );

                /*
                 * A cadência também permanece exatamente
                 * como estava antes da tentativa.
                 */
                assertFalse(
                    cadenceActive(
                        connection,
                        destination,
                        "CADENCE_V1"
                    )
                );

                assertTrue(
                    cadenceActive(
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
    void shouldBeIdempotentWhenBothDesiredVersionsAreAlreadyActive()
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
                    uniqueDestination();

                insertQuota(
                    connection,
                    destination,
                    "QUOTA_V3",
                    7,
                    true
                );

                insertCadence(
                    connection,
                    destination,
                    "CADENCE_V3",
                    5400L,
                    true
                );

                PublicationOperationalPolicyConfig desired =
                    configuration(
                        destination,
                        "QUOTA_V3",
                        7,
                        "CADENCE_V3",
                        Duration.ofMinutes(
                            90
                        )
                    );

                JdbcPublicationOperationalPolicySynchronizer
                    synchronizer =
                    new JdbcPublicationOperationalPolicySynchronizer(
                        connection
                    );

                synchronizer.synchronize(
                    desired
                );

                synchronizer.synchronize(
                    desired
                );

                assertTrue(
                    quotaActive(
                        connection,
                        destination,
                        "QUOTA_V3"
                    )
                );

                assertTrue(
                    cadenceActive(
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

    private PublicationOperationalPolicyConfig configuration(
        String destination,
        String quotaVersion,
        int maximum,
        String cadenceVersion,
        Duration interval
    ) {

        ZoneId zone =
            ZoneId.of(
                "America/Sao_Paulo"
            );

        return new PublicationOperationalPolicyConfig(
            CHANNEL,
            destination,
            new PublicationQuotaProfile(
                quotaVersion,
                maximum,
                zone
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
                zone
            )
        );
    }

    private void insertQuota(
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
                "America/Sao_Paulo"
            );

            statement.setBoolean(
                6,
                active
            );

            statement.executeUpdate();
        }
    }

    private void insertCadence(
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
                "America/Sao_Paulo"
            );

            statement.setBoolean(
                8,
                active
            );

            statement.executeUpdate();
        }
    }

    private boolean quotaActive(
        Connection connection,
        String destination,
        String version
    ) throws Exception {

        return active(
            connection,
            "publication_quota_profile",
            destination,
            version
        );
    }

    private boolean cadenceActive(
        Connection connection,
        String destination,
        String version
    ) throws Exception {

        return active(
            connection,
            "publication_cadence_profile",
            destination,
            version
        );
    }

    private boolean active(
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

                if (!resultSet.next()) {

                    return false;
                }

                return resultSet.getBoolean(
                    "active"
                );
            }
        }
    }

    private boolean quotaVersionExists(
        Connection connection,
        String destination,
        String version
    ) throws Exception {

        String sql =
            """
            SELECT 1
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

                return resultSet.next();
            }
        }
    }

    private String uniqueDestination() {

        return "@operational_policy_"
            + SEQUENCE.incrementAndGet()
            + "_"
            + Long.toUnsignedString(
            System.nanoTime()
        );
    }
}
