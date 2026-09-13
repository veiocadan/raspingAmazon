package com.raspingamazon.infrastructure.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DatabaseMigrationTest {

    @Test
    void shouldApplyDatabaseMigrations() {
        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String database = System.getenv("DB_NAME");
        String username = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        assertDoesNotThrow(() ->
                DatabaseMigration.migrate(
                        host,
                        port,
                        database,
                        username,
                        password
                )
        );
    }
}
