/*
 * FASE 12 - revisão final de idempotência.
 *
 * A identidade de DealCandidate não pode depender de collected_at.
 *
 * Uma reentrada da mesma ProcessingRun pode repetir coleta/parsing
 * depois de uma interrupção ocorrida antes do commit final e produzir
 * um novo timestamp para a mesma oferta lógica.
 *
 * Para a orquestração, a identidade idempotente do candidato é:
 *
 * processing_run_id + asin + source
 */

DO $$
BEGIN

    IF EXISTS (
        SELECT 1
        FROM deal_candidate
        GROUP BY
            processing_run_id,
            asin,
            source
        HAVING COUNT(*) > 1
    ) THEN

        RAISE EXCEPTION
            'Cannot strengthen DealCandidate idempotency because duplicate processing_run_id/asin/source rows already exist';

    END IF;

END
$$;


ALTER TABLE deal_candidate
    DROP CONSTRAINT uq_deal_candidate_observation;


ALTER TABLE deal_candidate
    ADD CONSTRAINT uq_deal_candidate_run_asin_source
        UNIQUE (
            processing_run_id,
            asin,
            source
        );
