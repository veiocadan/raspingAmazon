package com.raspingamazon.application.publication.outbox.retry;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Retry exponencial limitado para publicação externa.
 *
 * <p>Exemplo com:</p>
 *
 * <pre>
 * maxAttempts   = 5
 * initialBackoff = PT30S
 * maxBackoff     = PT5M
 *
 * tentativa 1 falhou -> +30s
 * tentativa 2 falhou -> +60s
 * tentativa 3 falhou -> +120s
 * tentativa 4 falhou -> +240s
 * tentativa 5 falhou -> terminal
 * </pre>
 *
 * <p>O limite superior impede crescimento indefinido do intervalo.</p>
 *
 * <p>Esta política é deliberadamente comum aos canais na FASE 19.
 * Políticas avançadas específicas por integração pertencem ao
 * hardening posterior.</p>
 */
public final class BoundedExponentialPublicationOutboxRetryPolicy
    implements PublicationOutboxRetryPolicy {

    private final int maxAttempts;

    private final Duration initialBackoff;

    private final Duration maxBackoff;

    public BoundedExponentialPublicationOutboxRetryPolicy(
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff
    ) {

        if (maxAttempts <= 0) {

            throw new IllegalArgumentException(
                "maxAttempts must be positive"
            );
        }

        this.initialBackoff =
            requirePositiveDuration(
                initialBackoff,
                "initialBackoff"
            );

        this.maxBackoff =
            requirePositiveDuration(
                maxBackoff,
                "maxBackoff"
            );

        if (this.maxBackoff.compareTo(
            this.initialBackoff
        ) < 0) {

            throw new IllegalArgumentException(
                "maxBackoff must not be shorter than initialBackoff"
            );
        }

        this.maxAttempts =
            maxAttempts;
    }

    @Override
    public Optional<OffsetDateTime> nextAttemptAt(
        int completedAttemptNumber,
        OffsetDateTime completedAt
    ) {

        if (completedAttemptNumber <= 0) {

            throw new IllegalArgumentException(
                "completedAttemptNumber must be positive"
            );
        }

        Objects.requireNonNull(
            completedAt,
            "completedAt must not be null"
        );

        /*
         * maxAttempts representa o número total de chamadas externas,
         * não a quantidade de retries adicionais.
         *
         * Portanto, se a tentativa que acabou de falhar já é a
         * maxAttempts, não existe nova tentativa.
         */
        if (completedAttemptNumber >= maxAttempts) {

            return Optional.empty();
        }

        Duration delay =
            calculateDelay(
                completedAttemptNumber
            );

        return Optional.of(
            completedAt.plus(
                delay
            )
        );
    }

    private Duration calculateDelay(
        int completedAttemptNumber
    ) {

        Duration delay =
            initialBackoff;

        /*
         * A falha da tentativa 1 utiliza initialBackoff.
         *
         * Cada tentativa subsequente dobra o intervalo até
         * maxBackoff.
         */
        for (int attempt = 1;
             attempt < completedAttemptNumber;
             attempt++) {

            if (delay.compareTo(
                maxBackoff
            ) >= 0) {

                return maxBackoff;
            }

            Duration doubled;

            try {

                doubled =
                    delay.multipliedBy(
                        2L
                    );

            } catch (ArithmeticException exception) {

                return maxBackoff;
            }

            if (doubled.compareTo(
                maxBackoff
            ) > 0) {

                return maxBackoff;
            }

            delay =
                doubled;
        }

        return delay;
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
