package com.raspingamazon.application.orchestration.worker;

import com.raspingamazon.application.orchestration.ProcessingJob;

/**
 * Executa a responsabilidade funcional associada a um ProcessingJob.
 *
 * <p>A implementação concreta decide qual caso de uso deve ser chamado
 * para cada ProcessingJobType.</p>
 */
@FunctionalInterface
public interface ProcessingJobExecutionPort {

    void execute(
        ProcessingJob job
    );
}
