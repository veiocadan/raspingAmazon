package com.raspingamazon.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {

    private DatabaseConnection() {
    }

    public static Connection open(
            String host,
            String port,
            String database,
            String username,
            String password
    ) throws SQLException {
        String jdbcUrl = "jdbc:postgresql://"
                + host
                + ":"
                + port
                + "/"
                + database;

        return DriverManager.getConnection(
                jdbcUrl,
                username,
                password
        );
    }
}
