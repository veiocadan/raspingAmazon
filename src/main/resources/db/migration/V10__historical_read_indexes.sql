/*
 * FASE 11-G2
 *
 * Índices de suporte às consultas históricas introduzidas
 * pela FASE 11.
 *
 * Esta migration não altera nenhuma regra de negócio.
 *
 * Objetivos:
 *
 * 1. acelerar localização do snapshot anterior, primeiro e último;
 * 2. acelerar navegação OfferSnapshot -> DealEvaluation;
 * 3. acelerar detecção de Publication PUBLISHED por avaliação.
 */


/* ================================================================
 * OFFER SNAPSHOT HISTORY
 * ================================================================ */

/*
 * Já existe a constraint idempotente:
 *
 * UNIQUE (
 *     product_id,
 *     collected_at,
 *     source
 * )
 *
 * Ela protege a identidade da coleta e também fornece um índice útil.
 *
 * Entretanto, as consultas históricas da FASE 11 possuem ordenação:
 *
 * product_id
 * collected_at
 * id
 *
 * O id é o desempate determinístico quando mais de um snapshot do
 * mesmo produto possuir o mesmo collected_at em fontes diferentes.
 *
 * Este índice atende diretamente:
 *
 * - snapshot anterior;
 * - primeiro snapshot;
 * - último snapshot;
 * - histórico ordenado.
 *
 * PostgreSQL pode percorrer um índice B-tree também no sentido
 * inverso, portanto a mesma estrutura atende leituras ASC e DESC.
 */
CREATE INDEX idx_offer_snapshot_history
    ON offer_snapshot (
        product_id,
        collected_at DESC,
        id DESC
    );


/* ================================================================
 * DEAL EVALUATION LOOKUP
 * ================================================================ */

/*
 * A foreign key:
 *
 * deal_evaluation.offer_snapshot_id
 *     -> offer_snapshot.id
 *
 * não cria automaticamente um índice no lado referenciador.
 *
 * A FASE 11 utiliza esse caminho para descobrir avaliações
 * relacionadas ao histórico de um produto.
 */
CREATE INDEX idx_deal_evaluation_offer_snapshot
    ON deal_evaluation (
        offer_snapshot_id
    );


/* ================================================================
 * PUBLICATION HISTORY LOOKUP
 * ================================================================ */

/*
 * A detecção "já publicada" executa conceitualmente:
 *
 * Product
 *     -> OfferSnapshot
 *     -> DealEvaluation
 *     -> Publication
 *
 * e exige:
 *
 * publication.status = 'PUBLISHED'
 *
 * O índice existente em publication.status continua sendo útil
 * para consultas globais por status.
 *
 * Este índice composto atende o caminho específico iniciado por
 * uma DealEvaluation:
 *
 * deal_evaluation_id
 * + status
 */
CREATE INDEX idx_publication_evaluation_status
    ON publication (
        deal_evaluation_id,
        status
    );
