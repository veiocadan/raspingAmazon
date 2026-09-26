package com.raspingamazon.application.operation.orchestration.run;

import com.raspingamazon.application.operation.orchestration.run.port.ProcessingRunOperationalDetailQueryPort;

import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso para obtenção do detalhe operacional de uma
 * ProcessingRun.
 */
public final class GetProcessingRunDetailUseCase {

    private final ProcessingRunOperationalDetailQueryPort
        queryPort;

    public GetProcessingRunDetailUseCase(
        ProcessingRunOperationalDetailQueryPort queryPort
    ) {

        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "GetProcessingRunDetailUseCase "
                    + "queryPort must not be null"
            );
    }

    public Optional<ProcessingRunDetail> execute(
        long runId
    ) {

        if (runId <= 0L) {

            throw new IllegalArgumentException(
                "runId must be positive"
            );
        }

        return Objects.requireNonNull(
            queryPort.findById(
                runId
            ),
            "ProcessingRunOperationalDetailQueryPort "
                + "must not return null"
        );
    }
}
