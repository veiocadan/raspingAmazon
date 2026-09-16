package com.raspingamazon.infrastructure.persistence;

import com.raspingamazon.infrastructure.config.ApplicationConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {

    private DatabaseConnection() {
    }

    public static Connection open(ApplicationConfig config) throws SQLException {
        String jdbcUrl = "jdbc:postgresql://"
                + config.databaseHost()
                + ":"
                + config.databasePort()
                + "/"
                + config.databaseName();

        return DriverManager.getConnection(
                jdbcUrl,
                config.databaseUser(),
                config.databasePassword()
        );
    }
}
