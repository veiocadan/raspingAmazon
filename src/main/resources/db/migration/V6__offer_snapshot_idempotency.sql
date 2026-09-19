/*
 * FASE 8.5-F3
 *
 * Define a identidade persistente de uma observação de oferta.
 *
 * Um mesmo produto observado pela mesma fonte no mesmo instante
 * de coleta representa o mesmo evento lógico.
 *
 * A restrição impede que retries ou reprocessamentos da mesma
 * CollectionResult criem snapshots históricos duplicados.
 */

ALTER TABLE offer_snapshot
    ADD CONSTRAINT uq_offer_snapshot_collection_identity
    UNIQUE (
        product_id,
        collected_at,
        source
    );