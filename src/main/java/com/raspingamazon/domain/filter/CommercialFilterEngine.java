package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;

import java.util.List;
import java.util.Objects;

/**
 * Motor de filtros comerciais configuráveis.
 *
 * Aplica todas as regras comerciais da FASE 9 em ordem estável
 * e retorna os resultados individuais para auditoria.
 *
 * Ordem atual:
 *
 * 1. desconto mínimo à vista;
 * 2. rating mínimo;
 * 3. quantidade mínima de avaliações.
 *
 * A ordem é deliberadamente estável e não representa score,
 * peso ou prioridade comercial.
 *
 * O motor não utiliza short-circuit. Todas as regras são executadas
 * mesmo quando uma regra anterior falha, permitindo preservar a
 * explicação completa da avaliação.
 *
 * Não pertencem a este motor:
 *
 * - elegibilidade de vendedor Amazon;
 * - elegibilidade de entrega Amazon;
 * - score;
 * - momentum;
 * - política de publicação.
 */
public final class CommercialFilterEngine {

    private final MinCashDiscountRule minCashDiscountRule;
    private final MinRatingRule minRatingRule;
    private final MinReviewCountRule minReviewCountRule;

    /**
     * Cria o motor com as implementações padrão das regras comerciais.
     */
    public CommercialFilterEngine() {
        this(
            new MinCashDiscountRule(),
            new MinRatingRule(),
            new MinReviewCountRule()
        );
    }

    /**
     * Construtor explícito utilizado para composição e testes.
     *
     * A ordem dos parâmetros também documenta a ordem estável
     * atualmente adotada pelo motor.
     */
    public CommercialFilterEngine(
        MinCashDiscountRule minCashDiscountRule,
        MinRatingRule minRatingRule,
        MinReviewCountRule minReviewCountRule
    ) {
        this.minCashDiscountRule =
            Objects.requireNonNull(
                minCashDiscountRule,
                "minCashDiscountRule must not be null"
            );

        this.minRatingRule =
            Objects.requireNonNull(
                minRatingRule,
                "minRatingRule must not be null"
            );

        this.minReviewCountRule =
            Objects.requireNonNull(
                minReviewCountRule,
                "minReviewCountRule must not be null"
            );
    }

    /**
     * Aplica todas as regras comerciais em ordem determinística.
     *
     * @param snapshot oferta observada
     * @param profile versão da configuração de filtros
     * @return lista imutável contendo exatamente um resultado
     *         para cada regra comercial vigente
     */
    public List<EvaluationRuleResult> evaluate(
        OfferSnapshot snapshot,
        FilterProfile profile
    ) {
        Objects.requireNonNull(
            snapshot,
            "snapshot must not be null"
        );

        Objects.requireNonNull(
            profile,
            "profile must not be null"
        );

        EvaluationRuleResult cashDiscountResult =
            minCashDiscountRule.evaluate(
                snapshot,
                profile
            );

        EvaluationRuleResult ratingResult =
            minRatingRule.evaluate(
                snapshot,
                profile
            );

        EvaluationRuleResult reviewCountResult =
            minReviewCountRule.evaluate(
                snapshot,
                profile
            );

        return List.of(
            cashDiscountResult,
            ratingResult,
            reviewCountResult
        );
    }
}
