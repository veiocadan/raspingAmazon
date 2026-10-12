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
                "development",
                "localhost",
                "5432",
                "rasping_amazon",
                "rasping",
                "secret"
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
                        "development",
                        "localhost",
                        "5432",
                        "rasping_amazon",
                        "rasping",
                        null
                )
        );
    }

    @Test
    void shouldRejectBlankValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ApplicationConfig(
                        "development",
                        "localhost",
                        "5432",
                        "rasping_amazon",
                        "rasping",
                        " "
                )
        );
    }

    @Test
    void shouldRedactDatabasePasswordInTextRepresentation() {
        String sensitivePassword = "sensitive-database-password-7d8c0e";

        ApplicationConfig config = new ApplicationConfig(
                "development",
                "localhost",
                "5432",
                "rasping_amazon",
                "rasping",
                sensitivePassword
        );

        String text = config.toString();

        assertTrue(text.startsWith("ApplicationConfig["));
        assertTrue(text.contains("databaseHost=localhost"));
        assertTrue(text.contains("databasePassword=<redacted>"));
        assertFalse(text.contains(sensitivePassword));
        assertFalse(String.valueOf(config).contains(sensitivePassword));

        // O JDBC continua recebendo a credencial pelo accessor explicito.
        assertEquals(sensitivePassword, config.databasePassword());
    }
}
