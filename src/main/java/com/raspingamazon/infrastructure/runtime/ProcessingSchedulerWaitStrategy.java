package com.raspingamazon.infrastructure.runtime;

import java.time.Duration;

/**
 * Estratégia de espera entre verificações do scheduler.
 *
 * <p>A abstração mantém o loop testável sem sleeps reais na suíte.</p>
 *
 * <p>Ela representa apenas polling operacional. Não representa retry
 * de ProcessingJob nem política de falhas da fonte.</p>
 */
@FunctionalInterface
public interface ProcessingSchedulerWaitStrategy {

    void await(
        Duration duration
    ) throws InterruptedException;
}
