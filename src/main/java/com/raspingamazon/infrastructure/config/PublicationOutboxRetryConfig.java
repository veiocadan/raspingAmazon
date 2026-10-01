package com.raspingamazon.infrastructure.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuração operacional do retry de entrega da publication outbox.
 *
 * <p>maxAttempts representa o número total máximo de chamadas
 * externas permitidas para uma mesma entrada da outbox.</p>
 *
 * <p>Exemplo:</p>
 *
 * <pre>
 * maxAttempts = 5
 *
 * tentativa 1
 * retry 1
 * retry 2
 * retry 3
 * retry 4
 *
 * total máximo = 5 chamadas externas
 * </pre>
 */
public record PublicationOutboxRetryConfig(
    int maxAttempts,
    Duration initialBackoff,
    Duration maxBackoff
) {

    public PublicationOutboxRetryConfig {

        if (maxAttempts <= 0) {

            throw new IllegalArgumentException(
                "maxAttempts must be positive"
            );
        }

        initialBackoff =
            requirePositiveDuration(
                initialBackoff,
                "initialBackoff"
            );

        maxBackoff =
            requirePositiveDuration(
                maxBackoff,
                "maxBackoff"
            );

        if (maxBackoff.compareTo(
            initialBackoff
        ) < 0) {

            throw new IllegalArgumentException(
                "maxBackoff must not be shorter than initialBackoff"
            );
        }
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
