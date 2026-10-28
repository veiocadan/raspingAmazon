package com.raspingamazon.infrastructure.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes locais: nenhuma chamada JDBC, recovery, coleta ou publicacao.
 * Mesmo um plano de teste autorizado usa um opener simulado.
 */
class DaemonStartupPolicyGateTest {

    @TempDir
    Path tempDir;

    private static final String AUTHORIZED = """
        {
          "schema_version": 1,
          "execution_mode": "supervised",
          "initial_schedule_interval": "PT1H",
          "daemon_start_authorized": true,
          "flyway_migrate_authorized": false,
          "external_actions_authorized": true,
          "automatic_restart_authorized": false,
          "shutdown_grace_seconds": 30
        }
        """;

    @Test
    void rejectsPreparationOnlyBeforeBootstrapSupplierRuns() throws Exception {
        Path policy = writePolicy(AUTHORIZED
            .replace("\"supervised\"", "\"preparation_only\"")
            .replace("\"daemon_start_authorized\": true", "\"daemon_start_authorized\": false")
            .replace("\"external_actions_authorized\": true", "\"external_actions_authorized\": false"));
        AtomicBoolean opened = new AtomicBoolean(false);
        StringWriter output = new StringWriter();

        int code = ContinuousProcessingMain.run(
            new PrintWriter(output, true), policy,
            () -> {
                opened.set(true);
                throw new AssertionError("Bootstrap must never be invoked");
            }
        );

        assertEquals(1, code);
        assertFalse(opened.get());
        assertTrue(output.toString().contains("IllegalStateException (details suppressed)"));
    }

    @Test
    void rejectsMissingPolicy() {
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(tempDir.resolve("missing.json")));
    }

    @Test
    void rejectsMalformedPolicy() throws Exception {
        Path policy = writePolicy("{broken-json");
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(policy));
    }

    @Test
    void rejectsDuplicateJsonKeys() throws Exception {
        Path policy = writePolicy(AUTHORIZED.replace(
            "\"schema_version\": 1,", "\"schema_version\": 1, \"schema_version\": 1,"));
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(policy));
    }

    @Test
    void rejectsUnknownExtraKey() throws Exception {
        Path policy = writePolicy(AUTHORIZED.replace(
            "\"shutdown_grace_seconds\": 30",
            "\"shutdown_grace_seconds\": 30, \"bypass\": true"));
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(policy));
    }

    @Test
    void rejectsTrailingSecondJsonDocument() throws Exception {
        Path policy = writePolicy(AUTHORIZED + "\n{\"extra\":true}");
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(policy));
    }

    @Test
    void rejectsUnsupportedVersion() throws Exception {
        Path policy = writePolicy(AUTHORIZED.replace(
            "\"schema_version\": 1", "\"schema_version\": 2"));
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(policy));
    }

    @Test
    void rejectsSupervisedModeWithoutDaemonApproval() throws Exception {
        Path policy = writePolicy(AUTHORIZED.replace(
            "\"daemon_start_authorized\": true",
            "\"daemon_start_authorized\": false"));
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(policy));
    }

    @Test
    void rejectsCoercedBooleanType() throws Exception {
        Path policy = writePolicy(AUTHORIZED.replace(
            "\"daemon_start_authorized\": true",
            "\"daemon_start_authorized\": \"true\""));
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(policy));
    }

    @Test
    void rejectsExternalEffectsWithoutApproval() throws Exception {
        Path policy = writePolicy(AUTHORIZED.replace(
            "\"external_actions_authorized\": true",
            "\"external_actions_authorized\": false"));
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(policy));
    }

    @Test
    void rejectsMigrationAuthorization() throws Exception {
        Path policy = writePolicy(AUTHORIZED.replace(
            "\"flyway_migrate_authorized\": false",
            "\"flyway_migrate_authorized\": true"));
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(policy));
    }

    @Test
    void rejectsAutomaticRestartAuthorization() throws Exception {
        Path policy = writePolicy(AUTHORIZED.replace(
            "\"automatic_restart_authorized\": false",
            "\"automatic_restart_authorized\": true"));
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(policy));
    }

    @Test
    void rejectsUnexpectedIntervalOrShutdownTime() throws Exception {
        Path invalidInterval = writePolicy(AUTHORIZED.replace("PT1H", "PT2H"));
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(invalidInterval));

        Path invalidShutdownGrace = writePolicy(AUTHORIZED.replace(
            "\"shutdown_grace_seconds\": 30",
            "\"shutdown_grace_seconds\": 5"));
        assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(invalidShutdownGrace));
    }

    @Test
    void doesNotLeakPolicyContentInError() throws Exception {
        String secret = "TOKEN_SUPER_SECRET_123";
        Path policy = writePolicy("{\"secret\":\"" + secret + "\"}");
        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> DaemonStartupPolicyGate.requireAuthorized(policy));
        assertFalse(error.getMessage().contains(secret));
        assertFalse(error.getMessage().contains(policy.toString()));
    }

    @Test
    void explicitSupervisedPolicyAllowsOnlyMockBootstrapInTest() throws Exception {
        Path policy = writePolicy(AUTHORIZED);
        assertDoesNotThrow(() -> DaemonStartupPolicyGate.requireAuthorized(policy));

        AtomicBoolean opened = new AtomicBoolean(false);
        StringWriter output = new StringWriter();
        int code = ContinuousProcessingMain.run(
            new PrintWriter(output, true), policy,
            () -> {
                opened.set(true);
                throw new IllegalStateException("TOKEN_SUPER_SECRET_123");
            }
        );
        assertTrue(opened.get());
        assertEquals(1, code);
        assertFalse(output.toString().contains("TOKEN_SUPER_SECRET_123"));
    }

    private Path writePolicy(String json) throws Exception {
        Path policy = tempDir.resolve("startup-policy.json");
        Files.writeString(policy, json, StandardCharsets.UTF_8);
        return policy;
    }
}
