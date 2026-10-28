package com.raspingamazon.infrastructure.runtime;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Fixture exclusiva de teste. Permite criar um runtime real com dois
 * loops simulados sem construir composicoes JDBC ou integracoes externas.
 */
public final class FailureLifecycleRuntimeFixture {

    private FailureLifecycleRuntimeFixture() {
    }

    public static ContinuousProcessingRuntime create(
        Consumer<BooleanSupplier> scheduler,
        Consumer<BooleanSupplier> worker
    ) {
        return new ContinuousProcessingRuntime(scheduler, worker);
    }
}
