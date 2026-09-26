/*
 * FASE 14
 *
 * Índice de suporte à consulta operacional paginada de DealEvaluation.
 *
 * A interface operacional utiliza keyset pagination com a ordenação:
 *
 * evaluated_at DESC
 * id DESC
 *
 * O id funciona como desempate determinístico quando duas avaliações
 * possuem exatamente o mesmo instante.
 *
 * Esta migration não altera regras de negócio nem o fluxo automático.
 */

CREATE INDEX idx_deal_evaluation_operational_order
    ON deal_evaluation (
        evaluated_at DESC,
        id DESC
    );
