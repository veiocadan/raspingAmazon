package com.raspingamazon.infrastructure.bootstrap;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;

/**
 * Contrato unico de politica para a entrada produtiva e a verificacao
 * preparatoria. Falha de modo fechado, sem expor valores ou caminhos.
 * Nenhum metodo deste tipo abre JDBC ou executa integracoes.
 */
final class DaemonStartupPolicyGate {

    private static final String POLICY_FILE =
        "config/operacao/continuous-startup-policy.v1.json";
    private static final long MAX_POLICY_BYTES = 16_384L;
    private static final Set<String> REQUIRED_KEYS = Set.of(
        "schema_version", "execution_mode", "initial_schedule_interval",
        "daemon_start_authorized", "flyway_migrate_authorized",
        "external_actions_authorized", "automatic_restart_authorized",
        "shutdown_grace_seconds"
    );

    private DaemonStartupPolicyGate() {
    }

    static Path defaultPolicyPath() {
        return Path.of(POLICY_FILE);
    }

    /** O entrypoint produtivo continua a exigir autorizacao de efeitos. */
    static void requireAuthorized(Path policyPath) {
        Policy plan = parse(policyPath);
        if (!"supervised".equals(plan.mode())
            || !plan.daemonStart()
            || !plan.externalActions()
            || plan.flywayMigrate()
            || plan.automaticRestart()) {
            throw rejected();
        }
    }

    /**
     * Diagnostico JDBC: permitida apenas a configuracao de preparacao,
     * com todas as quatro autorizacoes desligadas. Nao e um bypass do
     * metodo requireAuthorized nem libera ContinuousProcessingMain.
     */
    static void requirePreparationOnly(Path policyPath) {
        Policy plan = parse(policyPath);
        if (!"preparation_only".equals(plan.mode())
            || plan.daemonStart()
            || plan.externalActions()
            || plan.flywayMigrate()
            || plan.automaticRestart()) {
            throw rejected();
        }
    }

    private static Policy parse(Path policyPath) {
        if (policyPath == null) {
            throw rejected();
        }

        try {
            if (!Files.isRegularFile(policyPath, LinkOption.NOFOLLOW_LINKS)
                || Files.size(policyPath) > MAX_POLICY_BYTES) {
                throw rejected();
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

            final JsonNode root;
            try (Reader reader = Files.newBufferedReader(
                policyPath, StandardCharsets.UTF_8)) {
                root = mapper.readTree(reader);
            }

            if (root == null || !root.isObject()
                || root.size() != REQUIRED_KEYS.size()) {
                throw rejected();
            }
            for (String key : REQUIRED_KEYS) {
                if (!root.has(key)) {
                    throw rejected();
                }
            }

            JsonNode version = root.get("schema_version");
            JsonNode mode = root.get("execution_mode");
            JsonNode interval = root.get("initial_schedule_interval");
            JsonNode grace = root.get("shutdown_grace_seconds");

            if (!version.isInt() || version.intValue() != 1
                || !mode.isTextual()
                || !interval.isTextual()
                || !"PT1H".equals(interval.textValue())
                || !grace.isInt() || grace.intValue() != 30) {
                throw rejected();
            }

            return new Policy(
                mode.textValue(),
                flag(root, "daemon_start_authorized"),
                flag(root, "flyway_migrate_authorized"),
                flag(root, "external_actions_authorized"),
                flag(root, "automatic_restart_authorized")
            );

        } catch (IOException | SecurityException exception) {
            throw rejected();
        }
    }

    private static boolean flag(JsonNode root, String key) {
        JsonNode value = root.get(key);
        if (value == null || !value.isBoolean()) {
            throw rejected();
        }
        return value.booleanValue();
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException("Daemon startup authorization denied");
    }

    private record Policy(
        String mode,
        boolean daemonStart,
        boolean flywayMigrate,
        boolean externalActions,
        boolean automaticRestart
    ) {
    }
}
