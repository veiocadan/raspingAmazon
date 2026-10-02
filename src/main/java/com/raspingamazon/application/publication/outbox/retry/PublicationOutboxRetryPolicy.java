package com.raspingamazon.application.publication.outbox.retry;

import java.time.OffsetDateTime;
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
