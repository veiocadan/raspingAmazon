package com.raspingamazon.infrastructure.migration;

import com.raspingamazon.infrastructure.config.ApplicationConfig;
import org.flywaydb.core.Flyway;

public final class DatabaseMigration {

    private DatabaseMigration() {
    }

    public static void migrate(ApplicationConfig config) {
        String jdbcUrl = "jdbc:postgresql://"
                + config.databaseHost()
                + ":"
                + config.databasePort()
                + "/"
                + config.databaseName();

        Flyway flyway = Flyway.configure()
                .dataSource(
                        jdbcUrl,
                        config.databaseUser(),
                        config.databasePassword()
                )
                .load();

        flyway.migrate();
    }
}
