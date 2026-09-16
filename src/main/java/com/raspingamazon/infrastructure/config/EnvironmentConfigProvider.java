package com.raspingamazon.infrastructure.config;

public final class EnvironmentConfigProvider {

    private static final String DEFAULT_ENVIRONMENT = "development";
    private static final String DEFAULT_DATABASE_HOST = "localhost";
    private static final String DEFAULT_DATABASE_PORT = "5432";
    private static final String DEFAULT_DATABASE_NAME = "rasping_amazon";
    private static final String DEFAULT_DATABASE_USER = "rasping";

    private EnvironmentConfigProvider() {
    }

    public static ApplicationConfig load() {
        return new ApplicationConfig(
                readOrDefault("APP_ENV", DEFAULT_ENVIRONMENT),
                readOrDefault("DB_HOST", DEFAULT_DATABASE_HOST),
                readOrDefault("DB_PORT", DEFAULT_DATABASE_PORT),
                readOrDefault("DB_NAME", DEFAULT_DATABASE_NAME),
                readOrDefault("DB_USER", DEFAULT_DATABASE_USER),
                readRequired("DB_PASSWORD")
        );
    }

    private static String readOrDefault(String variableName, String defaultValue) {
        String value = System.getenv(variableName);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }

    private static String readRequired(String variableName) {
        String value = System.getenv(variableName);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable is missing: " + variableName
            );
        }

        return value;
    }
}
