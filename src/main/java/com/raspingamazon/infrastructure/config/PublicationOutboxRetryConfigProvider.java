package com.raspingamazon.infrastructure.config;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * Carrega a política básica de retry da publication outbox
 * a partir do ambiente.
 *
 * <p>Variáveis reconhecidas:</p>
 *
 * <pre>
 * PUBLICATION_OUTBOX_MAX_ATTEMPTS
 * PUBLICATION_OUTBOX_RETRY_INITIAL_BACKOFF
 * PUBLICATION_OUTBOX_RETRY_MAX_BACKOFF
 * </pre>
 *
 * <p>Os defaults apenas preservam uma configuração operacional
 * segura quando a aplicação é atualizada antes do arquivo .env.</p>
 *
 * <p>Todos os valores permanecem externamente configuráveis.</p>
 */
public final class PublicationOutboxRetryConfigProvider {

    public static final String MAX_ATTEMPTS_ENV =
        "PUBLICATION_OUTBOX_MAX_ATTEMPTS";

    public static final String INITIAL_BACKOFF_ENV =
        "PUBLICATION_OUTBOX_RETRY_INITIAL_BACKOFF";

    public static final String MAX_BACKOFF_ENV =
        "PUBLICATION_OUTBOX_RETRY_MAX_BACKOFF";

    private static final int DEFAULT_MAX_ATTEMPTS =
        5;

    private static final String DEFAULT_INITIAL_BACKOFF =
        "PT30S";

    private static final String DEFAULT_MAX_BACKOFF =
        "PT5M";

    private PublicationOutboxRetryConfigProvider() {
    }

    public static PublicationOutboxRetryConfig load() {

        return load(
            System.getenv()
        );
    }

    /**
     * Variante determinística para testes.
     */
    public static PublicationOutboxRetryConfig load(
        Map<String, String> environment
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        int maxAttempts =
            positiveInteger(
                MAX_ATTEMPTS_ENV,
                environment.get(
                    MAX_ATTEMPTS_ENV
                ),
                DEFAULT_MAX_ATTEMPTS
            );

        Duration initialBackoff =
            positiveDuration(
                INITIAL_BACKOFF_ENV,
                valueOrDefault(
                    environment,
                    INITIAL_BACKOFF_ENV,
                    DEFAULT_INITIAL_BACKOFF
                )
            );

        Duration maxBackoff =
            positiveDuration(
                MAX_BACKOFF_ENV,
                valueOrDefault(
                    environment,
                    MAX_BACKOFF_ENV,
                    DEFAULT_MAX_BACKOFF
                )
            );

        try {

            return new PublicationOutboxRetryConfig(
                maxAttempts,
                initialBackoff,
                maxBackoff
            );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                "Invalid publication outbox retry configuration",
                exception
            );
        }
    }

    private static int positiveInteger(
        String variableName,
        String rawValue,
        int defaultValue
    ) {

        if (rawValue == null
            || rawValue.isBlank()) {

            return defaultValue;
        }

        final int parsed;

        try {

            parsed =
                Integer.parseInt(
                    rawValue.trim()
                );

        } catch (NumberFormatException exception) {

            throw new IllegalStateException(
                variableName
                    + " must be a positive integer",
                exception
            );
        }

        if (parsed <= 0) {

            throw new IllegalStateException(
                variableName
                    + " must be a positive integer"
            );
        }

        return parsed;
    }

    private static Duration positiveDuration(
        String variableName,
        String rawValue
    ) {

        final Duration duration;

        try {

            duration =
                Duration.parse(
                    rawValue
                );

        } catch (DateTimeParseException exception) {

            throw new IllegalStateException(
                variableName
                    + " must contain a positive ISO-8601 duration",
                exception
            );
        }

        if (duration.isZero()
            || duration.isNegative()) {

            throw new IllegalStateException(
                variableName
                    + " must contain a positive ISO-8601 duration"
            );
        }

        return duration;
    }

    private static String valueOrDefault(
        Map<String, String> environment,
        String variableName,
        String defaultValue
    ) {

        String value =
            environment.get(
                variableName
            );

        if (value == null
            || value.isBlank()) {

            return defaultValue;
        }

        return value.trim();
    }
}
