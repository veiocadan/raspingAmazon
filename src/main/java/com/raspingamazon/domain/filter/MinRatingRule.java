package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Regra comercial que verifica se a avaliação agregada do produto
 * atende ao mínimo configurado no FilterProfile.
 *
 * A regra distingue explicitamente:
 *
 * - dado disponível e suficiente;
 * - dado disponível, mas abaixo do mínimo;
 * - dado indisponível ou inválido.
 *
 * A ausência de rating não é interpretada como zero.
 *
 * Esta regra não conhece persistência, PostgreSQL, Amazon, HTML
 * ou qualquer mecanismo de configuração externa.
 */
public final class MinRatingRule {

    private static final String RULE_CODE =
        CommercialFilterRuleCode.MIN_RATING.name();

    private static final String UNAVAILABLE =
        "UNAVAILABLE";

    private static final double MIN_VALID_RATING =
        0.0;

    private static final double MAX_VALID_RATING =
        5.0;

    /**
     * Avalia o rating observado no snapshot contra o limite configurado.
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
            format(profile.minRating());

        Double rating =
            snapshot.rating();

        if (!isValidRating(rating)) {
            return EvaluationRuleResult.failed(
                RULE_CODE,
                UNAVAILABLE,
                threshold,
                RejectionReason.RATING_UNAVAILABLE
            );
        }

        BigDecimal observedRating =
            BigDecimal.valueOf(rating);

        String observedValue =
            format(observedRating);

        if (observedRating.compareTo(profile.minRating()) < 0) {
            return EvaluationRuleResult.failed(
                RULE_CODE,
                observedValue,
                threshold,
                RejectionReason.RATING_BELOW_MINIMUM
            );
        }

        return EvaluationRuleResult.passed(
            RULE_CODE,
            observedValue,
            threshold
        );
    }

    /**
     * Confirma que o valor pode ser utilizado como rating de domínio.
     *
     * O contrato de coleta já prevê ratings entre 0 e 5 ou null.
     * Esta validação adicional protege a regra contra valores inválidos
     * introduzidos por integrações futuras ou construção manual de
     * snapshots.
     */
    private boolean isValidRating(Double rating) {

        if (rating == null) {
            return false;
        }

        if (!Double.isFinite(rating)) {
            return false;
        }

        return rating >= MIN_VALID_RATING
            && rating <= MAX_VALID_RATING;
    }

    /**
     * Produz representação textual estável para auditoria.
     *
     * Exemplos:
     *
     * 4.30 -> "4.3"
     * 4.0  -> "4"
     * 0.0  -> "0"
     */
    private String format(BigDecimal value) {
        return value
            .stripTrailingZeros()
            .toPlainString();
    }
}
