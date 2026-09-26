package com.raspingamazon.application.operation.orchestration.run;

import com.raspingamazon.application.operation.orchestration.run.port.ProcessingRunOperationalQueryPort;

import java.util.Objects;

/**
 * Caso de uso da listagem operacional de ProcessingRun.
 */
public final class ListProcessingRunsUseCase {

    private final ProcessingRunOperationalQueryPort queryPort;

    public ListProcessingRunsUseCase(
        ProcessingRunOperationalQueryPort queryPort
    ) {

        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "ListProcessingRunsUseCase queryPort must not be null"
            );
    }

    public ProcessingRunPage execute(
        ProcessingRunSearchCriteria criteria
    ) {

        Objects.requireNonNull(
            criteria,
            "ListProcessingRunsUseCase criteria must not be null"
        );

        return Objects.requireNonNull(
            queryPort.search(
                criteria
            ),
            "ProcessingRunOperationalQueryPort must not return null"
        );
    }
}
