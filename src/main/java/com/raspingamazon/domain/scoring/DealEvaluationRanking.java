package com.raspingamazon.domain.scoring;

import com.raspingamazon.domain.evaluation.DealEvaluation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Ordena avaliações pontuadas de forma determinística.
 *
 * <p>Regras da FASE 10:</p>
 *
 * <ol>
 *     <li>somente avaliações que possuam score participam;</li>
 *     <li>maior score vem primeiro;</li>
 *     <li>em empate de score, ASIN em ordem crescente.</li>
 * </ol>
 *
 * <p>O desempate por ASIN é exclusivamente técnico. Ele não representa
 * preferência comercial adicional e não altera o valor do score.</p>
 *
 * <p>Momentum, histórico e evolução temporal não pertencem a este
 * componente.</p>
 */
public final class DealEvaluationRanking {

    private static final Comparator<DealEvaluation> RANKING_COMPARATOR =
        Comparator
            .comparing(
                DealEvaluation::score,
                Comparator.reverseOrder()
            )
            .thenComparing(
                evaluation ->
                    evaluation
                        .offerSnapshot()
                        .product()
                        .asin()
                        .value()
            );

    /**
     * Retorna somente avaliações pontuadas, ordenadas segundo
     * o contrato determinístico da FASE 10.
     *
     * @param evaluations avaliações candidatas ao ranking
     * @return lista imutável de avaliações pontuadas e ordenadas
     */
    public List<DealEvaluation> rank(
        List<DealEvaluation> evaluations
    ) {
        Objects.requireNonNull(
            evaluations,
            "evaluations must not be null"
        );

        evaluations.forEach(
            evaluation ->
                Objects.requireNonNull(
                    evaluation,
                    "evaluations must not contain null"
                )
        );

        return evaluations.stream()
            .filter(
                evaluation ->
                    evaluation.score() != null
            )
            .sorted(
                RANKING_COMPARATOR
            )
            .toList();
    }
}
