package com.raspingamazon.infrastructure.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuração operacional do rate limiting preventivo das
 * integrações físicas utilizadas pelos canais de publicação.
 *
 * <p>Os intervalos representam throttling local deliberado da
 * aplicação.</p>
 *
 * <p>Eles não devem ser interpretados como declaração do limite
 * máximo oficial suportado por Telegram ou Meta.</p>
 */
public record PublicationRateLimitConfig(
    Duration telegramBotApiMinimumInterval,
    Duration whatsAppCloudApiMinimumInterval
) {

    public PublicationRateLimitConfig {

        telegramBotApiMinimumInterval =
            requirePositiveDuration(
                telegramBotApiMinimumInterval,
                "telegramBotApiMinimumInterval"
            );

        whatsAppCloudApiMinimumInterval =
            requirePositiveDuration(
                whatsAppCloudApiMinimumInterval,
                "whatsAppCloudApiMinimumInterval"
            );
    }

    private static Duration requirePositiveDuration(
        Duration value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (value.isZero()
            || value.isNegative()) {

            throw new IllegalArgumentException(
                fieldName + " must be positive"
            );
        }

        return value;
    }
}
