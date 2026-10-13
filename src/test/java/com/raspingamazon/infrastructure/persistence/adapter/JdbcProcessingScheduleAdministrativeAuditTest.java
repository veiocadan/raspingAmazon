package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.scheduling.ProcessingSchedule;
import com.raspingamazon.application.scheduling.ScheduleAdministrativeContext;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.testsupport.database.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Requer PostgreSQL isolado com Flyway V38 instalada. Nunca usa amazon-deals real. */
@PostgresIntegrationTest
class JdbcProcessingScheduleAdministrativeAuditTest {

    @Test
    void shouldAuditContextIdempotencyAndUncontextualizedMutationAtomically() throws Exception {
        String key = "audit-test-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        try (Connection con = DatabaseConnection.open(EnvironmentConfigProvider.load())) {
            con.setAutoCommit(false);
            JdbcProcessingScheduleAdapter adapter = new JdbcProcessingScheduleAdapter(con);
            try {
                adapter.saveIfAbsent(schedule(key, now));
                ScheduleAdministrativeContext ctx =
                    new ScheduleAdministrativeContext("unit-operator", "maintenance-ticket-42");
                assertFalse(adapter.pause(key, now, ctx).enabled());
                assertEquals(1, auditCount(con, key));
                assertEquals("PAUSE", auditField(con, key, "change_type"));
                assertEquals("unit-operator", auditField(con, key, "declared_actor"));
                assertEquals("maintenance-ticket-42", auditField(con, key, "change_reason"));
                assertEquals(databaseSessionUser(con), auditField(con, key, "db_session_role"));
                assertFalse(adapter.pause(key, now.plusSeconds(1), ctx).enabled());
                assertEquals(1, auditCount(con, key), "No semantic diff, no event");

                // Direct adapter calls remain recorded by the DB trigger, even without Java metadata.
                adapter.resume(key, now.plusMinutes(1), now.plusMinutes(1));
                assertEquals(2, auditCount(con, key));
                assertEquals("RESUME", auditField(con, key, "change_type"));
                assertTrue(auditField(con, key, "declared_actor").startsWith("db-role:"));
            } finally {
                con.rollback();  // Nenhuma linha de teste ou auditoria permanece no banco.
                con.setAutoCommit(true);
            }
        }
    }

    @Test
    void shouldRollbackStateAndAuditTogether() throws Exception {
        String key = "audit-rollback-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        try (Connection con = DatabaseConnection.open(EnvironmentConfigProvider.load())) {
            con.setAutoCommit(false);
            JdbcProcessingScheduleAdapter adapter = new JdbcProcessingScheduleAdapter(con);
            try {
                adapter.saveIfAbsent(schedule(key, now));
                // Commit da linha de teste nao e necessario: usamos SAVEPOINT para
                // desfazer somente o evento da pausa e preservar o registro ate o fim.
                java.sql.Savepoint beforeChange = con.setSavepoint();
                adapter.pause(key, now, new ScheduleAdministrativeContext("operator", "rollback-test"));
                assertEquals(1, auditCount(con, key));
                con.rollback(beforeChange);
                assertEquals(0, auditCount(con, key));
                assertTrue(adapter.findByKey(key).orElseThrow().enabled());
            } finally {
                con.rollback();
                con.setAutoCommit(true);
            }
        }
    }

    private ProcessingSchedule schedule(String key, OffsetDateTime now) {
        return new ProcessingSchedule(key, URI.create("https://www.amazon.com.br/deals"), true,
            Duration.ofHours(1), now.plusMinutes(2), null, null, null, null, now, now);
    }

    private int auditCount(Connection con, String key) throws Exception {
        try (PreparedStatement stmt = con.prepareStatement(
            "SELECT count(*) FROM public.processing_schedule_change_audit WHERE schedule_key = ?")) {
            stmt.setString(1, key);
            try (ResultSet result = stmt.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private String auditField(Connection con, String key, String field) throws Exception {
        // The field is a literal defined exclusively by these tests.
        assertTrue(java.util.Set.of("change_type", "declared_actor", "change_reason", "db_session_role")
            .contains(field));
        try (PreparedStatement stmt = con.prepareStatement(
            "SELECT " + field + " FROM public.processing_schedule_change_audit "
                + "WHERE schedule_key = ? ORDER BY id DESC LIMIT 1")) {
            stmt.setString(1, key);
            try (ResultSet result = stmt.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private String databaseSessionUser(Connection con) throws Exception {
        try (PreparedStatement stmt = con.prepareStatement("SELECT session_user");
             ResultSet result = stmt.executeQuery()) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
