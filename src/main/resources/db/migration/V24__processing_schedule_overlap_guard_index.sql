/*
 * FASE 17
 *
 * Suporte ao guard de sobreposição do scheduler.
 *
 * O scheduler consulta apenas o COLLECT_DEALS da última
 * ProcessingRun associada ao schedule e bloqueia uma nova janela
 * enquanto esse trabalho ainda estiver em estado não terminal.
 *
 * Estados considerados ativos:
 *
 * PENDING
 * RUNNING
 * RETRY_WAIT
 *
 * Estados terminais:
 *
 * SUCCEEDED
 * DEAD
 *
 * O índice parcial mantém essa verificação proporcional ao conjunto
 * de coletas ainda ativas, sem transformar processing_job em uma
 * varredura completa a cada poll do scheduler.
 */

CREATE INDEX idx_processing_job_active_collect_by_run
    ON processing_job (
        processing_run_id
    )
    WHERE job_type = 'COLLECT_DEALS'
      AND status IN (
          'PENDING',
          'RUNNING',
          'RETRY_WAIT'
      )
      AND processing_run_id IS NOT NULL;
