package com.raspingamazon.application.orchestration.failure;

import java.time.Duration;
import java.util.Objects;

/**
 * Backoff exponencial limitado.
 *
 * <p>Para base de 30 segundos:</p>
 *
 * <pre>
 * tentativa 1 -> 30 s
 * tentativa 2 -> 60 s
 * tentativa 3 -> 120 s
 * tentativa 4 -> 240 s
 * tentativa 5 -> limite configurado
 * </pre>
 */
public final class ExponentialRetryBackoffPolicy
    implements RetryBackoffPolicy {

    private final Duration baseDelay;

    private final Duration maxDelay;

    public ExponentialRetryBackoffPolicy(
        Duration baseDelay,
        Duration maxDelay
    ) {

        this.baseDelay =
            requirePositive(
                baseDelay,
                "baseDelay"
            );

        this.maxDelay =
            requirePositive(
                maxDelay,
                "maxDelay"
            );

        if (baseDelay.compareTo(
            maxDelay
        ) > 0) {

            throw new IllegalArgumentException(
                "baseDelay must not be greater than maxDelay"
            );
        }
    }

    @Override
    public Duration delayForAttempt(
        int attemptCount
    ) {

        if (attemptCount <= 0) {
            throw new IllegalArgumentException(
                "attemptCount must be positive"
            );
        }

        /*
         * Limitamos o expoente para evitar overflow antes mesmo
         * da aplicação do maxDelay.
         */
        int exponent =
            Math.min(
                attemptCount - 1,
                30
            );

        long multiplier =
            1L << exponent;

        Duration calculated;

        try {

            calculated =
                baseDelay.multipliedBy(
                    multiplier
                );

        } catch (ArithmeticException exception) {

            return maxDelay;
        }

        if (calculated.compareTo(
            maxDelay
        ) > 0) {

            return maxDelay;
        }

        return calculated;
    }

    private Duration requirePositive(
        Duration value,
        String name
    ) {

        Objects.requireNonNull(
            value,
            name + " must not be null"
        );

        if (value.isZero()
            || value.isNegative()) {

            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }

        return value;
    }
}
