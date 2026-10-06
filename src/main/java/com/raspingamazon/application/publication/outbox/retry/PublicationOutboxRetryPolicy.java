package com.raspingamazon.application.publication.outbox.retry;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Política básica de retry da outbox de publicação.
 *
 * <p>A política recebe o número da tentativa que acabou de falhar
 * e decide se uma nova tentativa deve ser agendada.</p>
 *
 * <p>A ausência de próximo instante significa que o orçamento de
 * retry terminou e a falha transitória passa a ser terminal.</p>
 *
 * <p>Quando um provider fornece um {@code retryNotBefore}, esse hint
 * funciona apenas como piso temporal. Ele pode atrasar um retry já
 * autorizado pela política local, mas nunca cria uma tentativa além
 * do orçamento local e nunca antecipa o backoff já calculado.</p>
 *
 * <p>A política não conhece PostgreSQL, PublicationChannel,
 * Telegram ou WhatsApp.</p>
 */
@FunctionalInterface
public interface PublicationOutboxRetryPolicy {

    /**
     * Decide quando executar a próxima tentativa.
     *
     * @param completedAttemptNumber número da tentativa que acabou
     *                               de ser persistida
     * @param completedAt instante em que a tentativa terminou
     * @return próximo instante, ou vazio quando não deve haver retry
     */
    Optional<OffsetDateTime> nextAttemptAt(
        int completedAttemptNumber,
        OffsetDateTime completedAt
    );

    /**
     * Combina a política local com um piso temporal eventualmente
     * informado pelo provider.
     *
     * <p>Regras:</p>
     *
     * <ul>
     *     <li>se a política local negar retry, o provider não pode
     *         criar uma tentativa adicional;</li>
     *     <li>se não houver hint, vale apenas a política local;</li>
     *     <li>se houver hint, vence o instante mais distante.</li>
     * </ul>
     */
    default Optional<OffsetDateTime> nextAttemptAt(
        int completedAttemptNumber,
        OffsetDateTime completedAt,
        Optional<OffsetDateTime> retryNotBefore
    ) {

        Objects.requireNonNull(
            retryNotBefore,
            "retryNotBefore must not be null"
        );

        Optional<OffsetDateTime> localDecision =
            nextAttemptAt(
                completedAttemptNumber,
                completedAt
            );

        if (localDecision.isEmpty()) {
            return Optional.empty();
        }

        OffsetDateTime localRetryAt =
            localDecision.orElseThrow();

        if (retryNotBefore.isEmpty()) {
            return Optional.of(
                localRetryAt
            );
        }

        OffsetDateTime providerFloor =
            retryNotBefore.orElseThrow();

        if (providerFloor.isAfter(
            localRetryAt
        )) {

            return Optional.of(
                providerFloor
            );
        }

        return Optional.of(
            localRetryAt
        );
    }

    /**
     * Política de compatibilidade que encerra a primeira falha
     * transitória sem reagendamento.
     *
     * <p>Ela permite preservar composições antigas enquanto a
     * política real da FASE 19 é conectada explicitamente.</p>
     */
    static PublicationOutboxRetryPolicy noRetry() {

        return (
            completedAttemptNumber,
            completedAt
        ) -> Optional.empty();
    }
}
