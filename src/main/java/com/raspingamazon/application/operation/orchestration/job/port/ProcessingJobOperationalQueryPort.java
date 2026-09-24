package com.raspingamazon.application.operation.orchestration.job.port;

import com.raspingamazon.application.operation.orchestration.job.ProcessingJobPage;
import com.raspingamazon.application.operation.orchestration.job.ProcessingJobSearchCriteria;

/**
 * Porta de consulta operacional da fila de ProcessingJob.
 */
@FunctionalInterface
public interface ProcessingJobOperationalQueryPort {

    ProcessingJobPage search(
        ProcessingJobSearchCriteria criteria
    );
}
