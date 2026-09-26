package com.raspingamazon.application.operation.orchestration.run;

import java.util.Objects;

/**
 * Read model detalhado de uma ProcessingRun para diagnóstico
 * operacional.
 *
 * <p>O resumo reutiliza ProcessingRunSummary, já utilizado pela
 * listagem operacional.</p>
 *
 * <p>As métricas complementares descrevem fatos persistidos da
 * execução e da fila.</p>
 *
 * <p>Este objeto não representa um novo agregado de domínio, não
 * participa da execução automática do pipeline e não recalcula
 * decisões comerciais.</p>
 */
public record ProcessingRunDetail(
    ProcessingRunSummary summary,
    ProcessingRunPipelineMetrics pipeline,
    ProcessingRunJobMetrics jobs
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
    }
}
