package com.raspingamazon.infrastructure.config;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * Carrega do ambiente a política operacional de throttling das
 * integrações externas de publicação.
 *
 * <p>Variáveis reconhecidas:</p>
 *
 * <pre>
 * PUBLICATION_RATE_LIMIT_TELEGRAM_BOT_API_MIN_INTERVAL
 * PUBLICATION_RATE_LIMIT_WHATSAPP_CLOUD_API_MIN_INTERVAL
 * </pre>
 *
 * <p>Os defaults são escolhas conservadoras do projeto e permanecem
 * substituíveis externamente sem recompilação.</p>
 */
public final class PublicationRateLimitConfigProvider {

    public static final String TELEGRAM_BOT_API_INTERVAL_ENV =
        "PUBLICATION_RATE_LIMIT_TELEGRAM_BOT_API_MIN_INTERVAL";

    public static final String WHATSAPP_CLOUD_API_INTERVAL_ENV =
        "PUBLICATION_RATE_LIMIT_WHATSAPP_CLOUD_API_MIN_INTERVAL";

    private static final String DEFAULT_TELEGRAM_BOT_API_INTERVAL =
        "PT1S";

    private static final String DEFAULT_WHATSAPP_CLOUD_API_INTERVAL =
        "PT1S";

    private PublicationRateLimitConfigProvider() {
    }

    public static PublicationRateLimitConfig load() {

        return load(
            System.getenv()
        );
    }

    /**
     * Variante determinística utilizada por testes.
     */
    public static PublicationRateLimitConfig load(
        Map<String, String> environment
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        Duration telegramBotApiMinimumInterval =
            positiveDuration(
                TELEGRAM_BOT_API_INTERVAL_ENV,
                valueOrDefault(
                    environment,
                    TELEGRAM_BOT_API_INTERVAL_ENV,
                    DEFAULT_TELEGRAM_BOT_API_INTERVAL
                )
            );

        Duration whatsAppCloudApiMinimumInterval =
            positiveDuration(
                WHATSAPP_CLOUD_API_INTERVAL_ENV,
                valueOrDefault(
                    environment,
                    WHATSAPP_CLOUD_API_INTERVAL_ENV,
                    DEFAULT_WHATSAPP_CLOUD_API_INTERVAL
                )
            );

        return new PublicationRateLimitConfig(
            telegramBotApiMinimumInterval,
            whatsAppCloudApiMinimumInterval
        );
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
