package com.raspingamazon.application.operation.orchestration.job;

import com.raspingamazon.application.operation.orchestration.job.port.ProcessingJobOperationalQueryPort;

import java.util.Objects;

/**
 * Caso de uso da listagem operacional de ProcessingJob.
 */
public final class ListProcessingJobsUseCase {

    private final ProcessingJobOperationalQueryPort queryPort;

    public ListProcessingJobsUseCase(
        ProcessingJobOperationalQueryPort queryPort
    ) {

        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "ListProcessingJobsUseCase queryPort must not be null"
            );
    }

    public ProcessingJobPage execute(
        ProcessingJobSearchCriteria criteria
    ) {

        Objects.requireNonNull(
            criteria,
            "ListProcessingJobsUseCase criteria must not be null"
        );

        return Objects.requireNonNull(
            queryPort.search(
                criteria
            ),
            "ProcessingJobOperationalQueryPort must not return null"
        );
    }
}
