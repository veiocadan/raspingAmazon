/*
 * FASE 17
 *
 * Estado persistente do agendamento e coordenação das execuções
 * recorrentes.
 *
 * Objetivos:
 *
 * 1. persistir frequência e próxima execução;
 * 2. permitir pausa sem apagar histórico;
 * 3. coordenar múltiplas instâncias por lease;
 * 4. preservar a janela lógica que originou a última ProcessingRun;
 * 5. permitir recuperação após restart;
 * 6. não criar uma segunda fila ou um segundo pipeline.
 *
 * Esta migration NÃO implementa coleta.
 * Esta migration NÃO implementa parsing.
 * Esta migration NÃO implementa retry de ProcessingJob.
 * Esta migration NÃO implementa canais de publicação.
 */


/* ================================================================
 * PROCESSING SCHEDULE
 * ================================================================ */

CREATE TABLE processing_schedule (

    /*
     * Identidade lógica estável do agendamento.
     *
     * Exemplo:
     *
     * amazon-deals
     */
                                     schedule_key TEXT PRIMARY KEY,

    /*
     * Fonte que deverá ser fornecida à ProcessingRun.
     */
                                     source_uri TEXT NOT NULL,

    /*
     * FALSE suspende somente a criação de novas execuções
     * automáticas.
     *
     * Runs, jobs e histórico existentes permanecem intactos.
     */
                                     enabled BOOLEAN NOT NULL,

    /*
     * Frequência persistida em milissegundos.
     *
     * A aplicação continua representando frequência por Duration.
     */
                                     interval_ms BIGINT NOT NULL,

    /*
     * Próxima janela lógica elegível.
     */
                                     next_run_at TIMESTAMPTZ NOT NULL,

    /*
     * Lease temporário utilizado para impedir que duas instâncias
     * adquiram a mesma janela.
     */
                                     lease_owner TEXT,
                                     lease_expires_at TIMESTAMPTZ,

    /*
     * Última janela efetivamente confirmada e a ProcessingRun
     * produzida por ela.
     */
                                     last_scheduled_for TIMESTAMPTZ,
                                     last_processing_run_id BIGINT,

                                     created_at TIMESTAMPTZ NOT NULL,
                                     updated_at TIMESTAMPTZ NOT NULL,

                                     CONSTRAINT ck_processing_schedule_key
                                         CHECK (
                                             BTRIM(schedule_key) <> ''
                                             ),

                                     CONSTRAINT ck_processing_schedule_source
                                         CHECK (
                                             BTRIM(source_uri) <> ''
                                             ),

                                     CONSTRAINT ck_processing_schedule_interval
                                         CHECK (
                                             interval_ms > 0
                                             ),

    /*
     * lease_owner e lease_expires_at formam uma unidade.
     */
                                     CONSTRAINT ck_processing_schedule_lease
                                         CHECK (
                                             (
                                                 lease_owner IS NULL
                                                     AND lease_expires_at IS NULL
                                                 )
                                                 OR
                                             (
                                                 lease_owner IS NOT NULL
                                                     AND BTRIM(lease_owner) <> ''
                                                     AND lease_expires_at IS NOT NULL
                                                 )
                                             ),

    /*
     * A última janela confirmada só existe quando existe também
     * uma ProcessingRun correspondente.
     */
                                     CONSTRAINT ck_processing_schedule_last_run
                                         CHECK (
                                             (
                                                 last_scheduled_for IS NULL
                                                     AND last_processing_run_id IS NULL
                                                 )
                                                 OR
                                             (
                                                 last_scheduled_for IS NOT NULL
                                                     AND last_processing_run_id IS NOT NULL
                                                     AND last_processing_run_id > 0
                                                 )
                                             ),

                                     CONSTRAINT ck_processing_schedule_updated_at
                                         CHECK (
                                             updated_at >= created_at
                                             ),

                                     CONSTRAINT fk_processing_schedule_last_run
                                         FOREIGN KEY (last_processing_run_id)
                                             REFERENCES processing_run (id)
);


/* ================================================================
 * INDEXES
 * ================================================================ */

/*
 * Índice principal do scheduler.
 *
 * A implementação consulta schedules habilitados cuja próxima
 * janela já venceu.
 */
CREATE INDEX idx_processing_schedule_due
    ON processing_schedule (
                            next_run_at,
                            schedule_key
        )
    WHERE enabled = TRUE;


/*
 * Suporte operacional à identificação de leases expirados.
 */
CREATE INDEX idx_processing_schedule_lease_expiration
    ON processing_schedule (
                            lease_expires_at,
                            schedule_key
        )
    WHERE lease_owner IS NOT NULL;
