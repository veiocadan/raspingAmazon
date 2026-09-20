package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;

import java.util.Objects;

/**
 * Regra comercial que verifica se a quantidade agregada de avaliações
 * do produto atende ao mínimo configurado no FilterProfile.
 *
 * A regra distingue explicitamente:
 *
 * - dado disponível e suficiente;
 * - dado disponível, mas abaixo do mínimo;
 * - dado indisponível ou inválido.
 *
 * A ausência de reviewCount não é interpretada como zero.
 *
 * Esta regra não conhece persistência, PostgreSQL, Amazon, HTML
 * ou qualquer mecanismo de configuração externa.
 */
public final class MinReviewCountRule {

    private static final String RULE_CODE =
        CommercialFilterRuleCode.MIN_REVIEW_COUNT.name();

    private static final String UNAVAILABLE =
        "UNAVAILABLE";

    /**
     * Avalia a quantidade de avaliações observada no snapshot contra
     * o limite configurado.
     *
     * @param snapshot fotografia da oferta avaliada
     * @param profile perfil de filtros comerciais
     * @return resultado auditável da regra
     */
    public EvaluationRuleResult evaluate(
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

        String threshold =
            Long.toString(
                profile.minReviewCount()
            );

        Long reviewCount =
            snapshot.reviewCount();

        if (!isValidReviewCount(reviewCount)) {
            return EvaluationRuleResult.failed(
                RULE_CODE,
                UNAVAILABLE,
                threshold,
                RejectionReason.REVIEW_COUNT_UNAVAILABLE
            );
        }

        String observedValue =
            Long.toString(reviewCount);

        if (reviewCount < profile.minReviewCount()) {
            return EvaluationRuleResult.failed(
                RULE_CODE,
                observedValue,
                threshold,
                RejectionReason.REVIEW_COUNT_BELOW_MINIMUM
            );
        }

        return EvaluationRuleResult.passed(
            RULE_CODE,
            observedValue,
            threshold
        );
    }

    /**
     * Confirma que o valor observado pode ser usado como quantidade
     * válida de avaliações.
     *
     * O contrato de coleta prevê valor inteiro maior ou igual a zero,
     * ou null quando a informação não está disponível.
     */
    private boolean isValidReviewCount(
        Long reviewCount
    ) {
        return reviewCount != null
            && reviewCount >= 0;
    }
}
