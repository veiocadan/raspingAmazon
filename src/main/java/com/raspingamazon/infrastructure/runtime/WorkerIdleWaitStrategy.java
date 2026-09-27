package com.raspingamazon.infrastructure.runtime;

import java.time.Duration;

/**
 * Estratégia operacional de espera utilizada quando o worker não
 * encontra trabalho disponível.
 *
 * <p>A abstração existe para manter o loop contínuo testável sem
 * introduzir sleeps reais na suíte.</p>
 *
 * <p>Ela não representa retry de ProcessingJob. Retry continua sendo
 * responsabilidade da orquestração durável da FASE 12.</p>
 */
@FunctionalInterface
public interface WorkerIdleWaitStrategy {

    /**
     * Aguarda antes da próxima consulta por trabalho.
     *
     * @param duration intervalo de espera
     * @throws InterruptedException quando a thread for interrompida
     */
    void await(
        Duration duration
    ) throws InterruptedException;
}
