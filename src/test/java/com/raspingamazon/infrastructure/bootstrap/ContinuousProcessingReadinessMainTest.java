package com.raspingamazon.infrastructure.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ContinuousProcessingReadinessMainTest {

    @TempDir Path tempDir;

    private static final String PREPARATION = """
        {
          "schema_version": 1,
          "execution_mode": "preparation_only",
          "initial_schedule_interval": "PT1H",
          "daemon_start_authorized": false,
          "flyway_migrate_authorized": false,
          "external_actions_authorized": false,
          "automatic_restart_authorized": false,
          "shutdown_grace_seconds": 30
        }
        """;

    @Test void acceptsOnlyPreparationPolicy() throws Exception {
        DaemonStartupPolicyGate.requirePreparationOnly(write(PREPARATION));
        assertThrows(IllegalStateException.class, () ->
            DaemonStartupPolicyGate.requireAuthorized(write(PREPARATION)));
    }

    @Test void rejectsSupervisedPolicyForReadiness() throws Exception {
        String json = PREPARATION.replace("preparation_only", "supervised")
            .replace("\"daemon_start_authorized\": false", "\"daemon_start_authorized\": true")
            .replace("\"external_actions_authorized\": false", "\"external_actions_authorized\": true");
        assertThrows(IllegalStateException.class, () ->
            DaemonStartupPolicyGate.requirePreparationOnly(write(json)));
        DaemonStartupPolicyGate.requireAuthorized(write(json));
    }

    @Test void rejectsEachUnsafePermission() throws Exception {
        for (String permission : new String[] {
            "daemon_start_authorized", "flyway_migrate_authorized",
            "external_actions_authorized", "automatic_restart_authorized" }) {
            String json = PREPARATION.replace("\"" + permission + "\": false",
                "\"" + permission + "\": true");
            assertThrows(IllegalStateException.class, () ->
                DaemonStartupPolicyGate.requirePreparationOnly(write(json)));
        }
    }

    @Test void rejectsDuplicateAndTrailingContent() throws Exception {
        assertThrows(IllegalStateException.class, () ->
            DaemonStartupPolicyGate.requirePreparationOnly(write(
                PREPARATION.replace("\"schema_version\": 1,",
                    "\"schema_version\": 1, \"schema_version\": 1,"))));
        assertThrows(IllegalStateException.class, () ->
            DaemonStartupPolicyGate.requirePreparationOnly(write(PREPARATION + " {}")));
    }

    @Test void rejectsUnexpectedSchemaAndTiming() throws Exception {
        assertThrows(IllegalStateException.class, () ->
            DaemonStartupPolicyGate.requirePreparationOnly(write(PREPARATION.replace("PT1H", "PT2H"))));
        assertThrows(IllegalStateException.class, () ->
            DaemonStartupPolicyGate.requirePreparationOnly(write(PREPARATION.replace(
                "\"shutdown_grace_seconds\": 30", "\"shutdown_grace_seconds\": 5"))));
        assertThrows(IllegalStateException.class, () ->
            DaemonStartupPolicyGate.requirePreparationOnly(write(PREPARATION.replace(
                "\"schema_version\": 1", "\"schema_version\": 2"))));
    }

    @Test void doesNotConnectWhenPolicyDeniesReadiness() throws Exception {
        AtomicBoolean connected = new AtomicBoolean();
        StringWriter buffer = new StringWriter();
        int code = ContinuousProcessingReadinessMain.run(
            new PrintWriter(buffer, true),
            write(PREPARATION.replace("preparation_only", "supervised")),
            () -> { connected.set(true); throw new AssertionError("must not connect"); }
        );
        assertEquals(1, code);
        assertFalse(connected.get());
        assertFalse(buffer.toString().contains("supervised"));
    }

    @Test void suppressesJdbcExceptionsWithoutSecrets() throws Exception {
        String secret = "PASSWORD_MARKER_MUST_NOT_APPEAR";
        StringWriter buffer = new StringWriter();
        int code = ContinuousProcessingReadinessMain.run(
            new PrintWriter(buffer, true), write(PREPARATION),
            () -> { throw new SQLException("jdbc password=" + secret); }
        );
        assertEquals(1, code);
        assertFalse(buffer.toString().contains(secret));
    }

    @Test void probesReadOnlySessionAndReturnsPass() throws Exception {
        FakeJdbc fake = new FakeJdbc();
        StringWriter buffer = new StringWriter();
        int code = ContinuousProcessingReadinessMain.run(
            new PrintWriter(buffer, true), write(PREPARATION), fake::connection);
        assertEquals(0, code);
        assertEquals("READINESS_JDBC_READ_ONLY_PASS", buffer.toString().trim());
        assertTrue(fake.readOnly);
        assertTrue(fake.rollbackCalled);
        assertTrue(fake.connectionClosed);
        assertEquals(5, fake.selects);
    }

    @Test void rejectsNonReadOnlyTransaction() throws Exception {
        FakeJdbc fake = new FakeJdbc();
        fake.transactionReadOnly = "off";
        StringWriter buffer = new StringWriter();
        int code = ContinuousProcessingReadinessMain.run(
            new PrintWriter(buffer, true), write(PREPARATION), fake::connection);
        assertEquals(1, code);
        assertEquals(1, fake.selects);
        assertTrue(fake.rollbackCalled);
    }

    @Test void rejectsWrongRuntimeRole() throws Exception {
        FakeJdbc fake = new FakeJdbc();
        fake.role = "rasping_migrator";
        StringWriter buffer = new StringWriter();
        int code = ContinuousProcessingReadinessMain.run(
            new PrintWriter(buffer, true), write(PREPARATION), fake::connection);
        assertEquals(1, code);
        assertEquals(1, fake.selects);
    }

    @Test void rejectsAddedScheduleAndPendingJobs() throws Exception {
        FakeJdbc extra = new FakeJdbc();
        extra.scheduleCount = 2;
        StringWriter output = new StringWriter();
        assertEquals(1, ContinuousProcessingReadinessMain.run(
            new PrintWriter(output, true), write(PREPARATION), extra::connection));
        FakeJdbc jobs = new FakeJdbc();
        jobs.jobCount = 1;
        assertEquals(1, ContinuousProcessingReadinessMain.run(
            new PrintWriter(new StringWriter(), true), write(PREPARATION), jobs::connection));
    }

    private Path write(String json) throws Exception {
        Path path = tempDir.resolve("readiness-policy.json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    /** SQL fake: avalia contrato sem conectar PostgreSQL de verdade. */
    private static final class FakeJdbc {
        boolean readOnly;
        boolean rollbackCalled;
        boolean connectionClosed;
        String transactionReadOnly = "on";
        String role = "rasping_runtime";
        long scheduleCount = 1;
        long jobCount = 0;
        int selects = 0;

        Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "setReadOnly" -> { readOnly = (boolean) args[0]; yield null; }
                    case "setTransactionIsolation", "setAutoCommit" -> null;
                    case "createStatement" -> statement();
                    case "rollback" -> { rollbackCalled = true; yield null; }
                    case "close" -> { connectionClosed = true; yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        }

        Statement statement() {
            return (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(), new Class<?>[] { Statement.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "setQueryTimeout", "close" -> null;
                    case "executeQuery" -> {
                        String sql = (String) args[0];
                        selects++;
                        if (sql.contains("transaction_read_only")) {
                            yield result(new Object[] {transactionReadOnly, role, "rasping_amazon"});
                        }
                        if (sql.contains("FROM processing_schedule")) {
                            yield result(new Object[] {scheduleCount, 1L});
                        }
                        if (sql.contains("FROM processing_job")) {
                            yield result(new Object[] {jobCount});
                        }
                        yield result(new Object[] {0L});
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        }

        ResultSet result(Object[] row) {
            boolean[] next = new boolean[1];
            return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(), new Class<?>[] { ResultSet.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> { boolean first = !next[0]; next[0] = true; yield first; }
                    case "getString" -> (String) row[((int) args[0]) - 1];
                    case "getLong" -> ((Number) row[((int) args[0]) - 1]).longValue();
                    case "wasNull" -> false;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        }
    }
}
