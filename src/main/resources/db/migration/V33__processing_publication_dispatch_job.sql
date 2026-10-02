/*
 * FASE 19
 *
 * Introduz PUBLICATION_DISPATCH como unidade durável de trabalho
 * da orquestração.
 *
 * O job pertence à ProcessingRun e será responsável, em etapa
 * posterior da implementação, por:
 *
 * ProcessingRun
 *      ↓
 * readiness
 *      ↓
 * seleção
 *      ↓
 * geração
 *      ↓
 * READY automático
 *      ↓
 * outbox
 *
 * Esta migration NÃO cria jobs automaticamente.
 *
 * A ativação do enqueue ocorrerá somente depois que o handler de
 * PUBLICATION_DISPATCH possuir semântica explícita para:
 *
 * - READY;
 * - IN_PROGRESS;
 * - BLOCKED.
 */


/* ================================================================
 * JOB TYPE
 * ================================================================ */

ALTER TABLE processing_job
    DROP CONSTRAINT ck_processing_job_type;

ALTER TABLE processing_job
    ADD CONSTRAINT ck_processing_job_type
        CHECK (
            job_type IN (
                'COLLECT_DEALS',
                'ENRICH_DEAL',
                'EVALUATE_DEAL',
                'PUBLICATION_DISPATCH'
            )
        );


/* ================================================================
 * SUBJECT
 * ================================================================ */

ALTER TABLE processing_job
    DROP CONSTRAINT ck_processing_job_subject;

ALTER TABLE processing_job
    ADD CONSTRAINT ck_processing_job_subject
        CHECK (
            (
                job_type = 'COLLECT_DEALS'
                AND processing_run_id IS NOT NULL
                AND deal_candidate_id IS NULL
                AND offer_snapshot_id IS NULL
            )
            OR
            (
                job_type = 'ENRICH_DEAL'
                AND processing_run_id IS NULL
                AND deal_candidate_id IS NOT NULL
                AND offer_snapshot_id IS NULL
            )
            OR
            (
                job_type = 'EVALUATE_DEAL'
                AND processing_run_id IS NULL
                AND deal_candidate_id IS NULL
                AND offer_snapshot_id IS NOT NULL
            )
            OR
            (
                job_type = 'PUBLICATION_DISPATCH'
                AND processing_run_id IS NOT NULL
                AND deal_candidate_id IS NULL
                AND offer_snapshot_id IS NULL
            )
        );


/* ================================================================
 * PROCESSING RUN INDEX
 * ================================================================ */

/*
 * Nenhum índice novo é necessário aqui.
 *
 * V21__processing_run_observability_read_index.sql já criou:
 *
 * idx_processing_job_processing_run
 *
 * sobre:
 *
 * processing_job (processing_run_id)
 *
 * WHERE processing_run_id IS NOT NULL
 *
 * Na época, esse índice atendia COLLECT_DEALS.
 *
 * PUBLICATION_DISPATCH também utiliza processing_run_id como sujeito
 * e passa naturalmente a reutilizar o mesmo índice.
 *
 * A identidade/idempotência do novo trabalho continua protegida pela
 * constraint já existente:
 *
 * uq_processing_job_idempotency (
 *     job_type,
 *     idempotency_key
 * )
 *
 * Portanto não criamos um índice redundante nesta migration.
 */
