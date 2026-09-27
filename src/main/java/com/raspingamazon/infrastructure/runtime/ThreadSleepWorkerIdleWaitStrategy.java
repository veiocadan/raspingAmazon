package com.raspingamazon.infrastructure.runtime;

import java.time.Duration;
import java.util.Objects;

/**
 * Estratégia padrão de espera do worker contínuo.
 *
 * <p>Thread.sleep pertence à infraestrutura operacional e não ao
 * ProcessingWorker da aplicação.</p>
 */
public final class ThreadSleepWorkerIdleWaitStrategy
    implements WorkerIdleWaitStrategy {

    @Override
    public void await(
        Duration duration
    ) throws InterruptedException {

        Objects.requireNonNull(
            duration,
            "duration must not be null"
        );

        if (duration.isNegative()) {
            throw new IllegalArgumentException(
                "duration must not be negative"
            );
        }

        Thread.sleep(
            duration
        );
    }
}
