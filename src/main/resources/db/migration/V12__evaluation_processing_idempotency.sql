/*
 * FASE 12-E0
 *
 * Protege a etapa EVALUATE_DEAL contra duplicação persistente.
 *
 * Um OfferSnapshot representa uma observação temporal única.
 * Para o pipeline assíncrono atual, essa observação possui no máximo
 * uma DealEvaluation persistida.
 *
 * A auditoria de momentum também pertence exatamente a uma avaliação.
 */

DO $$
BEGIN
    IF EXISTS (
        SELECT offer_snapshot_id
        FROM deal_evaluation
        GROUP BY offer_snapshot_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce DealEvaluation idempotency: duplicate evaluations exist for the same offer_snapshot_id';
    END IF;
END
$$;

ALTER TABLE deal_evaluation
    ADD CONSTRAINT uq_deal_evaluation_offer_snapshot
    UNIQUE (offer_snapshot_id);


/*
 * A mesma proteção é aplicada à auditoria de momentum.
 *
 * Um retry não pode produzir uma segunda auditoria para a mesma
 * DealEvaluation.
 */
DO $$
BEGIN
    IF EXISTS (
        SELECT deal_evaluation_id
        FROM deal_evaluation_momentum_audit
        GROUP BY deal_evaluation_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce MomentumAudit idempotency: duplicate audits exist for the same deal_evaluation_id';
    END IF;
END
$$;

ALTER TABLE deal_evaluation_momentum_audit
    ADD CONSTRAINT uq_momentum_audit_deal_evaluation
    UNIQUE (deal_evaluation_id);
