package com.raspingamazon.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApplicationConfigTest {

    @Test
    void shouldCreateConfigurationWithValidValues() {
        ApplicationConfig config = new ApplicationConfig(
                "development", "localhost", "5432", "rasping_amazon", "rasping", "secret"
        );

        assertEquals("development", config.environment());
        assertEquals("localhost", config.databaseHost());
        assertEquals("5432", config.databasePort());
        assertEquals("rasping_amazon", config.databaseName());
        assertEquals("rasping", config.databaseUser());
        assertEquals("secret", config.databasePassword());
    }

    @Test
    void shouldRejectNullValues() {
        assertThrows(
                NullPointerException.class,
                () -> new ApplicationConfig(
                        "development", "localhost", "5432", "rasping_amazon", "rasping", null
                )
        );
    }

    @Test
    void shouldRejectBlankValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApplicationConfig(
                        "development", "localhost", "5432", "rasping_amazon", "rasping", " "
                )
        );
    }

    @Test
    void shouldRedactDatabasePasswordInTextRepresentation() {
        String sensitivePassword = "sensitive-database-password-7d8c0e";

        ApplicationConfig config = new ApplicationConfig(
                "development", "localhost", "5432", "rasping_amazon", "rasping", sensitivePassword
        );

        String text = config.toString();

        assertTrue(text.startsWith("ApplicationConfig["));
        assertTrue(text.contains("databaseHost=localhost"));
        assertTrue(text.contains("databasePassword=<redacted>"));
        assertFalse(text.contains(sensitivePassword));
        assertFalse(String.valueOf(config).contains(sensitivePassword));
        assertEquals(sensitivePassword, config.databasePassword());
    }

    @Test
    void shouldKeepLegacyConstructorCompatibleForMigration() {
        ApplicationConfig config = new ApplicationConfig(
                "development", "localhost", "5432", "rasping_amazon", "rasping", "legacy-secret"
        );

        assertEquals("rasping", config.databaseMigrationUser());
        assertEquals("legacy-secret", config.databaseMigrationPassword());
    }

    @Test
    void shouldSupportDistinctRuntimeAndMigrationIdentities() {
        ApplicationConfig config = new ApplicationConfig(
                "development", "localhost", "5432", "rasping_amazon",
                "rasping_runtime", "runtime-secret", "rasping_migrator", "migration-secret"
        );

        assertEquals("rasping_runtime", config.databaseUser());
        assertEquals("runtime-secret", config.databasePassword());
        assertEquals("rasping_migrator", config.databaseMigrationUser());
        assertEquals("migration-secret", config.databaseMigrationPassword());
    }

    @Test
    void shouldRedactBothPasswordsWhenIdentitiesAreDistinct() {
        String runtimeSecret = "runtime-sensitive-91d";
        String migrationSecret = "migration-sensitive-4ac";
        ApplicationConfig config = new ApplicationConfig(
                "development", "localhost", "5432", "rasping_amazon",
                "rasping_runtime", runtimeSecret, "rasping_migrator", migrationSecret
        );

        String text = config.toString();
        assertTrue(text.contains("databasePassword=<redacted>"));
        assertTrue(text.contains("databaseMigrationPassword=<redacted>"));
        assertFalse(text.contains(runtimeSecret));
        assertFalse(text.contains(migrationSecret));
    }

    @Test
    void shouldRejectInvalidMigrationPassword() {
        assertThrows(NullPointerException.class, () -> new ApplicationConfig(
                "development", "localhost", "5432", "rasping_amazon",
                "rasping_runtime", "runtime-secret", "rasping_migrator", null
        ));
        assertThrows(IllegalArgumentException.class, () -> new ApplicationConfig(
                "development", "localhost", "5432", "rasping_amazon",
                "rasping_runtime", "runtime-secret", "rasping_migrator", " "
        ));
    }
}
