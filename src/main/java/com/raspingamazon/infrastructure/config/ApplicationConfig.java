package com.raspingamazon.infrastructure.config;

import java.util.Objects;

/**
 * Configuracao basica da aplicacao.
 *
 * <p>A senha de banco permanece acessivel ao composition root por meio do
 * accessor do record, mas nunca aparece na representacao textual.</p>
 */
public record ApplicationConfig(
        String environment,
        String databaseHost,
        String databasePort,
        String databaseName,
        String databaseUser,
        String databasePassword
) {

    public ApplicationConfig {
        environment = requireValue("environment", environment);
        databaseHost = requireValue("databaseHost", databaseHost);
        databasePort = requireValue("databasePort", databasePort);
        databaseName = requireValue("databaseName", databaseName);
        databaseUser = requireValue("databaseUser", databaseUser);
        databasePassword = requireValue("databasePassword", databasePassword);
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
