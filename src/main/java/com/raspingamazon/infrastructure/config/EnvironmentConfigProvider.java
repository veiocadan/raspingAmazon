package com.raspingamazon.infrastructure.config;

import java.util.Map;
import java.util.Objects;

/**
 * Carrega os parametros do banco exclusivamente de variaveis de ambiente.
 *
 * <p>A identidade de migracao e opcional durante a transicao, mas suas duas
 * variaveis devem estar presentes juntas. Configuracao parcial falha antes
 * de qualquer tentativa de conexao.</p>
 */
public final class EnvironmentConfigProvider {

    private static final String DEFAULT_ENVIRONMENT = "development";
    private static final String DEFAULT_DATABASE_HOST = "localhost";
    private static final String DEFAULT_DATABASE_PORT = "5432";
    private static final String DEFAULT_DATABASE_NAME = "rasping_amazon";
    private static final String DEFAULT_DATABASE_USER = "rasping";

    private static final String MIGRATION_USER = "DB_MIGRATION_USER";
    private static final String MIGRATION_PASSWORD = "DB_MIGRATION_PASSWORD";

    private EnvironmentConfigProvider() {
    }

    public static ApplicationConfig load() {
        return load(System.getenv());
    }

    // Visibilidade de pacote permite testar o contrato sem mutar o ambiente
    // global do processo e sem acessar credenciais reais.
    static ApplicationConfig load(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment must not be null");

        String runtimeUser = readOrDefault(
                environment, "DB_USER", DEFAULT_DATABASE_USER
        );
        String runtimePassword = readRequired(environment, "DB_PASSWORD");

        boolean userProvided = environment.containsKey(MIGRATION_USER);
        boolean passwordProvided = environment.containsKey(MIGRATION_PASSWORD);

        if (userProvided != passwordProvided) {
            throw new IllegalStateException(
                    "DB_MIGRATION_USER and DB_MIGRATION_PASSWORD must be configured together"
            );
        }

        String migrationUser = runtimeUser;
        String migrationPassword = runtimePassword;

        if (userProvided) {
            migrationUser = readRequired(environment, MIGRATION_USER);
            migrationPassword = readRequired(environment, MIGRATION_PASSWORD);

            if (migrationUser.equals(runtimeUser)) {
                throw new IllegalStateException(
                        "DB_MIGRATION_USER must identify a role different from DB_USER"
                );
            }
        }

        return new ApplicationConfig(
                readOrDefault(environment, "APP_ENV", DEFAULT_ENVIRONMENT),
                readOrDefault(environment, "DB_HOST", DEFAULT_DATABASE_HOST),
                readOrDefault(environment, "DB_PORT", DEFAULT_DATABASE_PORT),
                readOrDefault(environment, "DB_NAME", DEFAULT_DATABASE_NAME),
                runtimeUser,
                runtimePassword,
                migrationUser,
                migrationPassword
        );
    }

    private static String readOrDefault(
            Map<String, String> environment,
            String variableName,
            String defaultValue
    ) {
        String value = environment.get(variableName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static String readRequired(
            Map<String, String> environment,
            String variableName
    ) {
        String value = environment.get(variableName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable is missing or blank: " + variableName
            );
        }
        return value;
    }
}
