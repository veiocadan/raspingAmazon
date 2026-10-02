package com.raspingamazon.application.publication.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * Regra de admissão de uma integração física de publicação.
 *
 * <p>Um canal lógico não precisa corresponder a uma integração
 * física exclusiva.</p>
 *
 * <p>Por exemplo:</p>
 *
 * <pre>
 * TELEGRAM
 *          \
 *           -> TELEGRAM_BOT_API
 *          /
 * WHATSAPP_MANUAL
 *
 *
 * WHATSAPP
 *     -> WHATSAPP_CLOUD_API
 * </pre>
 *
 * <p>Essa separação permite que canais diferentes que compartilham
 * o mesmo provider também compartilhem a mesma capacidade.</p>
 */
public record PublicationRateLimitRule(
    String integrationKey,
    Duration minimumInterval
) {

    public PublicationRateLimitRule {

        integrationKey =
            requireText(
                integrationKey,
                "integrationKey"
            );

        Objects.requireNonNull(
            minimumInterval,
            "minimumInterval must not be null"
        );

        if (minimumInterval.isZero()
            || minimumInterval.isNegative()) {

            throw new IllegalArgumentException(
                "minimumInterval must be positive"
            );
        }
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return trimmed;
    }
}
