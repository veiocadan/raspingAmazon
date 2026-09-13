package com.raspingamazon.infrastructure.migration;

import org.flywaydb.core.Flyway;

public final class DatabaseMigration {

    private DatabaseMigration() {
    }

    public static void migrate(
            String host,
            String port,
            String database,
            String username,
            String password
    ) {
        String jdbcUrl = "jdbc:postgresql://"
                + host
                + ":"
                + port
                + "/"
                + database;

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .load();

        flyway.migrate();
    }
}