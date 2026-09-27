package com.raspingamazon.infrastructure.runtime;

import java.time.Duration;
import java.util.Objects;

/**
 * Implementação padrão da espera entre verificações do scheduler.
 */
public final class ThreadSleepProcessingSchedulerWaitStrategy
    implements ProcessingSchedulerWaitStrategy {

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
