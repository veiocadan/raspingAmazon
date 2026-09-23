package com.raspingamazon.application.orchestration.failure;

import java.time.Duration;

/**
 * Define o intervalo entre tentativas de um job transitório.
 */
@FunctionalInterface
public interface RetryBackoffPolicy {

    /**
     * Calcula o atraso para a próxima tentativa.
     *
     * @param attemptCount número da tentativa que acabou de falhar
     * @return atraso antes da próxima execução
     */
    Duration delayForAttempt(
        int attemptCount
    );
}
