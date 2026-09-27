/*
 * FASE 16 — Observabilidade
 *
 * Completa a correlação persistente entre a fronteira durável
 * produzida pelo parsing:
 *
 * ProcessingRun
 *      ↓
 * DealCandidate
 *
 * e a observação enriquecida:
 *
 * OfferSnapshot
 *      ↓
 * DealEvaluation
 *      ↓
 * Publication
 *
 * O vínculo é deliberadamente nullable.
 *
 * Um DealCandidate pode existir sem ter sido enriquecido ainda,
 * por exemplo enquanto seu ENRICH_DEAL estiver:
 *
 * PENDING
 * RUNNING
 * RETRY_WAIT
 * DEAD
 *
 * Portanto:
 *
 * ausência de offer_snapshot_id
 *     ≠
 * inconsistência estrutural
 *
 * Ela significa apenas que ainda não existe correlação persistida
 * com um OfferSnapshot.
 *
 * A coluna também NÃO é UNIQUE.
 *
 * A idempotência de DealCandidate continua sendo:
 *
 * processing_run_id + asin + source
 *
 * e a identidade de OfferSnapshot continua sendo:
 *
 * product_id + collected_at + source
 *
 * Esta migration não altera nenhuma dessas semânticas.
 */


/* ================================================================
 * CORRELAÇÃO DEAL CANDIDATE -> OFFER SNAPSHOT
 * ================================================================ */

ALTER TABLE deal_candidate
    ADD COLUMN offer_snapshot_id BIGINT;


/*
 * A referência aponta para o fato temporal enriquecido que corresponde
 * ao candidato.
 *
 * Não é utilizado ON DELETE CASCADE.
 *
 * OfferSnapshot é fato histórico persistente e seu ciclo de vida não
 * deve ser controlado pela remoção de um DealCandidate.
 */
ALTER TABLE deal_candidate
    ADD CONSTRAINT fk_deal_candidate_offer_snapshot
        FOREIGN KEY (offer_snapshot_id)
            REFERENCES offer_snapshot (id);


/* ================================================================
 * BACKFILL
 * ================================================================
 *
 * Antes da FASE 16 o vínculo já podia ser reconstruído pela aplicação
 * utilizando:
 *
 * ASIN
 * + collected_at
 * + source
 *
 * Esse é exatamente o critério usado pelo lookup de enrichment já
 * persistido.
 *
 * Product possui unicidade por ASIN.
 *
 * OfferSnapshot possui unicidade por:
 *
 * product_id
 * + collected_at
 * + source
 *
 * Portanto, quando existe um snapshot correspondente, a associação é
 * determinística.
 *
 * Candidatos que nunca chegaram ao enrichment continuam com
 * offer_snapshot_id NULL.
 */

UPDATE deal_candidate AS candidate
SET offer_snapshot_id = snapshot.id
    FROM product
INNER JOIN offer_snapshot AS snapshot
ON snapshot.product_id = product.id
WHERE candidate.offer_snapshot_id IS NULL
  AND product.asin = candidate.asin
  AND snapshot.collected_at = candidate.collected_at
  AND snapshot.source = candidate.source;


/* ================================================================
 * ÍNDICE DE CORRELAÇÃO
 * ================================================================
 *
 * processing_run_id já possui índice próprio criado pela orquestração.
 *
 * Este índice suporta o caminho inverso:
 *
 * OfferSnapshot
 *      ↓
 * DealCandidate
 *      ↓
 * ProcessingRun
 *
 * e futuras consultas operacionais de linhagem.
 */

CREATE INDEX idx_deal_candidate_offer_snapshot
    ON deal_candidate (
                       offer_snapshot_id
        )
    WHERE offer_snapshot_id IS NOT NULL;
