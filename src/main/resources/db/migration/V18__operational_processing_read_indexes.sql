/*
 * FASE 14
 *
 * Índices de suporte às consultas operacionais de ProcessingRun
 * e ProcessingJob.
 *
 * Esta migration não altera semântica da orquestração,
 * política de retry, lease, scheduler ou regras comerciais.
 */


/* ================================================================
 * PROCESSING RUN
 * ================================================================ */

/*
 * A listagem operacional de runs utiliza keyset pagination:
 *
 * requested_at DESC
 * id DESC
 *
 * O id fornece desempate determinístico para runs solicitadas
 * no mesmo instante.
 */
CREATE INDEX idx_processing_run_operational_order
    ON processing_run (
        requested_at DESC,
        id DESC
    );


/* ================================================================
 * PROCESSING JOB
 * ================================================================ */

/*
 * A listagem operacional de jobs utilizará:
 *
 * created_at DESC
 * id DESC
 *
 * O índice específico de claim já existente continua responsável
 * pelo caminho crítico do worker:
 *
 * status + available_at + id
 *
 * Este novo índice é exclusivamente para observação operacional.
 */
CREATE INDEX idx_processing_job_operational_order
    ON processing_job (
        created_at DESC,
        id DESC
    );
