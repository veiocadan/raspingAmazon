package com.raspingamazon.application.operation.orchestration.run.port;

import com.raspingamazon.application.operation.orchestration.run.ProcessingRunDetail;

import java.util.Optional;

/**
 * Porta de leitura do detalhe operacional de uma ProcessingRun.
 *
 * <p>Ela é separada da porta de listagem para manter consultas
 * paginadas e consultas agregadas de detalhe como contratos
 * independentes.</p>
 */
@FunctionalInterface
public interface ProcessingRunOperationalDetailQueryPort {

    Optional<ProcessingRunDetail> findById(
        long runId
    );
}
