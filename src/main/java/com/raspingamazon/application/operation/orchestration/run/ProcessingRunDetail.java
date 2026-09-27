package com.raspingamazon.application.operation.orchestration.run;

import java.util.List;
import java.util.Objects;

/**
 * Read model detalhado de uma ProcessingRun para diagnóstico
 * operacional.
 *
 * <p>O resumo reutiliza ProcessingRunSummary, já utilizado pela
 * listagem operacional.</p>
 *
 * <p>As métricas complementares descrevem fatos persistidos da
 * execução, da fila e das fronteiras de integração.</p>
 *
 * <p>Este objeto não representa um novo agregado de domínio, não
 * participa da execução automática do pipeline e não recalcula
 * decisões comerciais.</p>
 */
public record ProcessingRunDetail(
    ProcessingRunSummary summary,
    ProcessingRunPipelineMetrics pipeline,
    ProcessingRunJobMetrics jobs,
    List<ProcessingRunIntegrationMetrics> integrations
) {

    public ProcessingRunDetail {

        Objects.requireNonNull(
            summary,
            "ProcessingRunDetail summary must not be null"
        );

        Objects.requireNonNull(
            pipeline,
            "ProcessingRunDetail pipeline must not be null"
        );

        Objects.requireNonNull(
            jobs,
            "ProcessingRunDetail jobs must not be null"
        );

        integrations =
            List.copyOf(
                Objects.requireNonNull(
                    integrations,
                    "ProcessingRunDetail integrations must not be null"
                )
            );
    }

    /**
     * Compatibilidade para consumidores que ainda não precisam das
     * métricas de integração.
     */
    public ProcessingRunDetail(
        ProcessingRunSummary summary,
        ProcessingRunPipelineMetrics pipeline,
        ProcessingRunJobMetrics jobs
    ) {

        this(
            summary,
            pipeline,
            jobs,
            List.of()
        );
    }
}
