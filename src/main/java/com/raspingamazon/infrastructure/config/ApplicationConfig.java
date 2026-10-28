package com.raspingamazon.infrastructure.config;

import java.util.Objects;

/**
 * Configuracao de ambiente e de acesso ao PostgreSQL.
 *
 * <p>As credenciais do runtime JDBC e do Flyway sao independentes quando
 * DB_MIGRATION_USER e DB_MIGRATION_PASSWORD estao configuradas. O construtor
 * legado de seis argumentos preserva a compatibilidade durante a transicao.</p>
 *
 * <p>Segredos nunca sao expostos pela representacao textual do record.</p>
 */
public record ApplicationConfig(
        String environment,
        String databaseHost,
        String databasePort,
        String databaseName,
        String databaseUser,
        String databasePassword,
        String databaseMigrationUser,
        String databaseMigrationPassword
) {

    public ApplicationConfig {
        environment = requireValue("environment", environment);
        databaseHost = requireValue("databaseHost", databaseHost);
        databasePort = requireValue("databasePort", databasePort);
        databaseName = requireValue("databaseName", databaseName);
        databaseUser = requireValue("databaseUser", databaseUser);
        databasePassword = requireValue("databasePassword", databasePassword);
        databaseMigrationUser = requireValue("databaseMigrationUser", databaseMigrationUser);
        databaseMigrationPassword = requireValue("databaseMigrationPassword", databaseMigrationPassword);
    }

    /**
     * Construtor compativel com os consumidores existentes.
     *
     * <p>Quando nenhuma identidade de migracao especifica foi provisionada,
     * o Flyway continua usando a identidade do runtime, sem alterar os
     * pontos de composicao existentes.</p>
     */
    public ApplicationConfig(
            String environment,
            String databaseHost,
            String databasePort,
            String databaseName,
            String databaseUser,
            String databasePassword
    ) {
        this(
                environment,
                databaseHost,
                databasePort,
                databaseName,
                databaseUser,
                databasePassword,
                databaseUser,
                databasePassword
        );
    }

    @Override
    public String toString() {
        return "ApplicationConfig["
                + "environment=" + environment
                + ", databaseHost=" + databaseHost
                + ", databasePort=" + databasePort
                + ", databaseName=" + databaseName
                + ", databaseUser=" + databaseUser
                + ", databasePassword=<redacted>"
                + ", databaseMigrationUser=" + databaseMigrationUser
                + ", databaseMigrationPassword=<redacted>"
                + "]";
    }

    private static String requireValue(String name, String value) {
        Objects.requireNonNull(value, name + " must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }

        return value;
    }
}
