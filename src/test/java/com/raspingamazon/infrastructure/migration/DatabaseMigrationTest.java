package com.raspingamazon.infrastructure.migration;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DatabaseMigrationTest {

    @Test
    void shouldApplyDatabaseMigrations() {
        ApplicationConfig config = EnvironmentConfigProvider.load();

        assertDoesNotThrow(() ->
                DatabaseMigration.migrate(config)
        );
    }
}
