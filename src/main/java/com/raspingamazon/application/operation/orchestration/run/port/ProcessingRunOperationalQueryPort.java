package com.raspingamazon.application.operation.orchestration.run.port;

import com.raspingamazon.application.operation.orchestration.run.ProcessingRunPage;
import com.raspingamazon.application.operation.orchestration.run.ProcessingRunSearchCriteria;

/**
 * Porta de consulta operacional de ProcessingRun.
 */
@FunctionalInterface
public interface ProcessingRunOperationalQueryPort {

    ProcessingRunPage search(
        ProcessingRunSearchCriteria criteria
    );
}
