/*
 * FASE 16 — Observabilidade
 *
 * Índice de suporte à leitura operacional detalhada de ProcessingRun.
 *
 * A linhagem dos ProcessingJobs não utiliza uma única coluna:
 *
 * COLLECT_DEALS
 *     -> processing_run_id
 *
 * ENRICH_DEAL
 *     -> deal_candidate_id
 *
 * EVALUATE_DEAL
 *     -> offer_snapshot_id
 *
 * Os índices para deal_candidate_id e offer_snapshot_id já pertencem
 * à fundação da fila persistente.
 *
 * A consulta operacional detalhada por run também precisa localizar
 * eficientemente o job COLLECT_DEALS através de processing_run_id.
 *
 * PostgreSQL não cria automaticamente um índice na coluna filha de
 * uma foreign key. Portanto, a existência da FK processing_job ->
 * processing_run não substitui este índice.
 *
 * Este índice é exclusivamente de suporte à leitura/observabilidade.
 *
 * Ele:
 *
 * - não altera a identidade de ProcessingJob;
 * - não altera claim;
 * - não altera retry;
 * - não altera lease;
 * - não altera idempotência;
 * - não cria scheduler;
 * - não altera decisões comerciais.
 *
 * O predicado parcial evita indexar os jobs cujos sujeitos pertencem
 * às etapas ENRICH_DEAL e EVALUATE_DEAL, pois nesses casos
 * processing_run_id é deliberadamente NULL.
 */

CREATE INDEX idx_processing_job_processing_run
    ON processing_job (
                       processing_run_id
        )
    WHERE processing_run_id IS NOT NULL;
