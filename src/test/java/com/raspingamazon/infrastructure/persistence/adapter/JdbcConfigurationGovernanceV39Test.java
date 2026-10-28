package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.scheduling.ProcessingSchedule;
import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FASE 21-D. Usa exclusivamente o PostgreSQL de testes preparado pela
 * extensao @PostgresIntegrationTest. Cada caso faz rollback integral.
 */
@PostgresIntegrationTest
class JdbcConfigurationGovernanceV39Test {

    @Test
    void shouldNotAuditOnlyNextRunAtEvenWithAdministrativeReason() throws Exception {
        String key = "governance-schedule-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        try (Connection connection = openTransaction()) {
            try {
                JdbcProcessingScheduleAdapter adapter =
                    new JdbcProcessingScheduleAdapter(connection);
                adapter.saveIfAbsent(new ProcessingSchedule(
                    key,
                    URI.create("https://www.amazon.com.br/deals"),
                    true,
                    Duration.ofHours(1),
                    now.plusMinutes(2),
                    null, null, null, null,
                    now, now
                ));

                setLocal(connection, "rasping.schedule_actor", "integration-operator");
                setLocal(connection, "rasping.schedule_reason", "GOV-21D-scheduler-test");

                try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE public.processing_schedule "
                        + "SET next_run_at = next_run_at + INTERVAL '1 minute' "
                        + "WHERE schedule_key = ?")) {
                    statement.setString(1, key);
                    assertEquals(1, statement.executeUpdate());
                }
                assertEquals(0, scheduleAuditCount(connection, key));

                try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE public.processing_schedule SET interval_ms = interval_ms + 1000 "
                        + "WHERE schedule_key = ?")) {
                    statement.setString(1, key);
                    assertEquals(1, statement.executeUpdate());
                }
                assertEquals(1, scheduleAuditCount(connection, key));
                assertEquals("CHANGE_INTERVAL", latestScheduleAuditType(connection, key));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void shouldProtectSelectionVersionsAndRecordProvenance() throws Exception {
        String channel = unique("SELECTION");
        String destination = unique("destination");
        try (Connection connection = openTransaction()) {
            try {
                setLocal(connection, "rasping.config_actor", "integration-operator");
                setLocal(connection, "rasping.config_reason", "ticket-GOV-21D");
                JdbcPublicationSelectionProfileSynchronizer synchronizer =
                    new JdbcPublicationSelectionProfileSynchronizer(connection);
                PublicationSelectionProfile v1 = new PublicationSelectionProfile(
                    "GOV_TEST_V1", Duration.ofSeconds(60), Duration.ofSeconds(120));
                PublicationSelectionProfile v2 = new PublicationSelectionProfile(
                    "GOV_TEST_V2", Duration.ofSeconds(90), Duration.ofSeconds(180));

                verifyVersionedProfile(
                    connection, "publication_selection_profile", channel, destination,
                    () -> synchronizer.synchronize(channel, destination, v1),
                    () -> synchronizer.synchronize(channel, destination, v2),
                    "UPDATE public.publication_selection_profile "
                        + "SET hard_cooldown_seconds = hard_cooldown_seconds + 1 "
                        + "WHERE channel = ? AND destination = ? AND version = 'GOV_TEST_V1'",
                    "integration-operator", "ticket-GOV-21D"
                );
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void shouldProtectQuotaVersionsAndUseRoleFallback() throws Exception {
        String channel = unique("QUOTA");
        String destination = unique("destination");
        try (Connection connection = openTransaction()) {
            try {
                JdbcPublicationQuotaProfileSynchronizer synchronizer =
                    new JdbcPublicationQuotaProfileSynchronizer(connection);
                PublicationQuotaProfile v1 = new PublicationQuotaProfile(
                    "GOV_TEST_V1", 5, ZoneId.of("America/Sao_Paulo"));
                PublicationQuotaProfile v2 = new PublicationQuotaProfile(
                    "GOV_TEST_V2", 8, ZoneId.of("America/Sao_Paulo"));

                verifyVersionedProfile(
                    connection, "publication_quota_profile", channel, destination,
                    () -> synchronizer.synchronize(channel, destination, v1),
                    () -> synchronizer.synchronize(channel, destination, v2),
                    "UPDATE public.publication_quota_profile "
                        + "SET max_publications_per_day = max_publications_per_day + 1 "
                        + "WHERE channel = ? AND destination = ? AND version = 'GOV_TEST_V1'",
                    null, "profile-version:GOV_TEST_V2"
                );
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void shouldProtectCadenceVersionsAndRecordProvenance() throws Exception {
        String channel = unique("CADENCE");
        String destination = unique("destination");
        try (Connection connection = openTransaction()) {
            try {
                setLocal(connection, "rasping.config_actor", "integration-operator");
                setLocal(connection, "rasping.config_reason", "ticket-GOV-21D");
                JdbcPublicationCadenceProfileSynchronizer synchronizer =
                    new JdbcPublicationCadenceProfileSynchronizer(connection);
                PublicationCadenceProfile v1 = new PublicationCadenceProfile(
                    "GOV_TEST_V1", Duration.ofMinutes(30),
                    LocalTime.of(8, 0), LocalTime.of(20, 0),
                    ZoneId.of("America/Sao_Paulo"));
                PublicationCadenceProfile v2 = new PublicationCadenceProfile(
                    "GOV_TEST_V2", Duration.ofMinutes(45),
                    LocalTime.of(9, 0), LocalTime.of(21, 0),
                    ZoneId.of("America/Sao_Paulo"));

                verifyVersionedProfile(
                    connection, "publication_cadence_profile", channel, destination,
                    () -> synchronizer.synchronize(channel, destination, v1),
                    () -> synchronizer.synchronize(channel, destination, v2),
                    "UPDATE public.publication_cadence_profile "
                        + "SET interval_seconds = interval_seconds + 1 "
                        + "WHERE channel = ? AND destination = ? AND version = 'GOV_TEST_V1'",
                    "integration-operator", "ticket-GOV-21D"
                );
            } finally {
                connection.rollback();
            }
        }
    }

    private void verifyVersionedProfile(
        Connection connection,
        String table,
        String channel,
        String destination,
        Runnable firstVersion,
        Runnable secondVersion,
        String forbiddenValueChangeSql,
        String expectedActor,
        String expectedReference
    ) throws Exception {
        assertProfileTable(table);
        firstVersion.run();
        assertEquals(1, profileCount(connection, table, channel, destination));
        assertTrue(isVersionActive(connection, table, channel, destination, "GOV_TEST_V1"));

        // Repetir a versao corrente com os mesmos valores e idempotente.
        firstVersion.run();
        assertEquals(1, profileCount(connection, table, channel, destination));

        // Uma transacao desfeita devolve V1 ao estado ativo, sem deixar V2.
        Savepoint beforeReplacement = connection.setSavepoint();
        secondVersion.run();
        assertEquals(2, profileCount(connection, table, channel, destination));
        connection.rollback(beforeReplacement);
        assertEquals(1, profileCount(connection, table, channel, destination));
        assertTrue(isVersionActive(connection, table, channel, destination, "GOV_TEST_V1"));

        secondVersion.run();
        assertEquals(2, profileCount(connection, table, channel, destination));
        secondVersion.run();
        assertEquals(2, profileCount(connection, table, channel, destination));

        assertFalse(isVersionActive(connection, table, channel, destination, "GOV_TEST_V1"));
        assertTrue(isVersionActive(connection, table, channel, destination, "GOV_TEST_V2"));

        // Mesmo uma desativacao direta deixa data, origem e referencia.
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT deactivated_at, deactivation_actor, deactivation_reference "
                + "FROM public." + table + " "
                + "WHERE channel = ? AND destination = ? AND version = 'GOV_TEST_V1'")) {
            statement.setString(1, channel);
            statement.setString(2, destination);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertNotNull(result.getObject("deactivated_at"));
                if (expectedActor == null) {
                    assertTrue(result.getString("deactivation_actor").startsWith("db-role:"));
                    assertEquals("profile-deactivation:GOV_TEST_V1",
                        result.getString("deactivation_reference"));
                } else {
                    assertEquals(expectedActor, result.getString("deactivation_actor"));
                    assertEquals(expectedReference, result.getString("deactivation_reference"));
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT activation_previous_version, activation_actor, "
                + "activation_reference, activated_at "
                + "FROM public." + table + " "
                + "WHERE channel = ? AND destination = ? AND version = 'GOV_TEST_V2'")) {
            statement.setString(1, channel);
            statement.setString(2, destination);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("GOV_TEST_V1", result.getString("activation_previous_version"));
                String actualActor = result.getString("activation_actor");
                if (expectedActor == null) {
                    assertTrue(actualActor.startsWith("db-role:"));
                } else {
                    assertEquals(expectedActor, actualActor);
                }
                assertEquals(expectedReference, result.getString("activation_reference"));
                assertNotNull(result.getObject("activated_at"));
            }
        }

        assertSqlRejected(connection, forbiddenValueChangeSql, channel, destination);
        assertSqlRejected(connection,
            "UPDATE public." + table + " SET active = true "
                + "WHERE channel = ? AND destination = ? AND version = 'GOV_TEST_V1'",
            channel, destination);
        assertSqlRejected(connection,
            "DELETE FROM public." + table + " "
                + "WHERE channel = ? AND destination = ? AND version = 'GOV_TEST_V1'",
            channel, destination);
        assertEquals(2, profileCount(connection, table, channel, destination));
    }

    private Connection openTransaction() throws SQLException {
        Connection connection = DatabaseConnection.open(EnvironmentConfigProvider.load());
        connection.setAutoCommit(false);
        return connection;
    }

    private void setLocal(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT set_config(?, ?, true)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            try (ResultSet ignored = statement.executeQuery()) {
                assertTrue(ignored.next());
            }
        }
    }

    private void assertSqlRejected(
        Connection connection, String sql, String channel, String destination
    ) throws SQLException {
        Savepoint beforeAttempt = connection.setSavepoint();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, channel);
            statement.setString(2, destination);
            assertThrows(SQLException.class, statement::executeUpdate);
        } finally {
            connection.rollback(beforeAttempt);
        }
    }

    private int profileCount(
        Connection connection, String table, String channel, String destination
    ) throws SQLException {
        assertProfileTable(table);
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT count(*) FROM public." + table + " "
                + "WHERE channel = ? AND destination = ?")) {
            statement.setString(1, channel);
            statement.setString(2, destination);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private boolean isVersionActive(
        Connection connection, String table, String channel, String destination, String version
    ) throws SQLException {
        assertProfileTable(table);
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT active FROM public." + table
                + " WHERE channel = ? AND destination = ? AND version = ?")) {
            statement.setString(1, channel);
            statement.setString(2, destination);
            statement.setString(3, version);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private void assertProfileTable(String table) {
        assertTrue(Set.of(
            "publication_selection_profile",
            "publication_quota_profile",
            "publication_cadence_profile"
        ).contains(table));
    }

    private int scheduleAuditCount(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT count(*) FROM public.processing_schedule_change_audit "
                + "WHERE schedule_key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private String latestScheduleAuditType(Connection connection, String key)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT change_type FROM public.processing_schedule_change_audit "
                + "WHERE schedule_key = ? ORDER BY id DESC LIMIT 1")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
