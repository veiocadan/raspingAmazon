package com.raspingamazon.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentConfigProviderTest {

    private static final String RUNTIME_SECRET = "runtime-only-password-7f2";
    private static final String MIGRATION_SECRET = "migration-only-password-e91";

    private static Map<String, String> basicEnvironment() {
        return new HashMap<>(Map.of(
                "DB_USER", "rasping_runtime",
                "DB_PASSWORD", RUNTIME_SECRET
        ));
    }

    @Test
    void shouldUseLegacyCredentialsWhenMigrationVariablesAreAbsent() {
        ApplicationConfig config = EnvironmentConfigProvider.load(basicEnvironment());

        assertEquals("rasping_runtime", config.databaseUser());
        assertEquals(RUNTIME_SECRET, config.databasePassword());
        assertEquals("rasping_runtime", config.databaseMigrationUser());
        assertEquals(RUNTIME_SECRET, config.databaseMigrationPassword());
        assertEquals("localhost", config.databaseHost());
        assertEquals("5432", config.databasePort());
    }

    @Test
    void shouldIsolateExplicitMigrationCredentialsFromRuntime() {
        Map<String, String> environment = basicEnvironment();
        environment.put("DB_MIGRATION_USER", "rasping_migrator");
        environment.put("DB_MIGRATION_PASSWORD", MIGRATION_SECRET);

        ApplicationConfig config = EnvironmentConfigProvider.load(environment);

        assertEquals("rasping_runtime", config.databaseUser());
        assertEquals(RUNTIME_SECRET, config.databasePassword());
        assertEquals("rasping_migrator", config.databaseMigrationUser());
        assertEquals(MIGRATION_SECRET, config.databaseMigrationPassword());
        assertFalse(config.toString().contains(RUNTIME_SECRET));
        assertFalse(config.toString().contains(MIGRATION_SECRET));
    }

    @Test
    void shouldRejectOnlyMigrationUserWithoutExposingRuntimePassword() {
        Map<String, String> environment = basicEnvironment();
        environment.put("DB_MIGRATION_USER", "rasping_migrator");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> EnvironmentConfigProvider.load(environment)
        );
        assertTrue(exception.getMessage().contains("DB_MIGRATION_PASSWORD"));
        assertFalse(exception.getMessage().contains(RUNTIME_SECRET));
    }

    @Test
    void shouldRejectOnlyMigrationPasswordWithoutExposingItsValue() {
        Map<String, String> environment = basicEnvironment();
        environment.put("DB_MIGRATION_PASSWORD", MIGRATION_SECRET);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> EnvironmentConfigProvider.load(environment)
        );
        assertTrue(exception.getMessage().contains("DB_MIGRATION_USER"));
        assertFalse(exception.getMessage().contains(MIGRATION_SECRET));
    }

    @Test
    void shouldRejectBlankExplicitMigrationCredentials() {
        Map<String, String> environment = basicEnvironment();
        environment.put("DB_MIGRATION_USER", "rasping_migrator");
        environment.put("DB_MIGRATION_PASSWORD", "  ");

        assertThrows(IllegalStateException.class, () -> EnvironmentConfigProvider.load(environment));
        environment.put("DB_MIGRATION_PASSWORD", MIGRATION_SECRET);
        environment.put("DB_MIGRATION_USER", "  ");
        assertThrows(IllegalStateException.class, () -> EnvironmentConfigProvider.load(environment));
    }

    @Test
    void shouldRejectExplicitSameRoleAsRuntime() {
        Map<String, String> environment = basicEnvironment();
        environment.put("DB_MIGRATION_USER", "rasping_runtime");
        environment.put("DB_MIGRATION_PASSWORD", MIGRATION_SECRET);

        assertThrows(IllegalStateException.class, () -> EnvironmentConfigProvider.load(environment));
    }

    @Test
    void shouldRejectMissingRuntimePassword() {
        assertThrows(IllegalStateException.class, () -> EnvironmentConfigProvider.load(Map.of()));
    }

    @Test
    void shouldNotNormalizePasswordsOrRequireRealCredentialsForUnitTests() {
        Map<String, String> environment = basicEnvironment();
        environment.put("DB_MIGRATION_USER", "rasping_migrator");
        environment.put("DB_MIGRATION_PASSWORD", "  special spaces  ");

        ApplicationConfig config = EnvironmentConfigProvider.load(environment);
        assertEquals("  special spaces  ", config.databaseMigrationPassword());
    }
}
