package com.raspingamazon.domain.evaluation;

import java.util.Objects;

/**
 * Resultado individual de uma regra aplicada durante a avaliação.
 *
 * <p>Uma DealEvaluation representa a decisão agregada.
 * EvaluationRuleResult representa a explicação granular de cada
 * regra que participou dessa decisão.</p>
 *
 * <p>Exemplo:</p>
 *
 * <pre>
 * ruleCode      = SELLER_IS_AMAZON
 * passed        = false
 * observedValue = THIRD_PARTY
 * threshold     = AMAZON
 * reasonCode    = SELLER_THIRD_PARTY
 * </pre>
 */
public record EvaluationRuleResult(
        String ruleCode,
        boolean passed,
        String observedValue,
        String threshold,
        RejectionReason reasonCode
) {

    public EvaluationRuleResult {

        ruleCode = requireText(
                ruleCode,
                "EvaluationRuleResult ruleCode must not be blank"
        );

        observedValue = requireText(
                observedValue,
                "EvaluationRuleResult observedValue must not be blank"
        );

        threshold = requireText(
                threshold,
                "EvaluationRuleResult threshold must not be blank"
        );

        /*
         * Uma regra que passou não possui motivo de rejeição.
         */
        if (passed && reasonCode != null) {
            throw new IllegalArgumentException(
                    "Passed rule must not have reasonCode"
            );
        }

        /*
         * Uma regra que falhou precisa explicar por que falhou.
         */
        if (!passed && reasonCode == null) {
            throw new IllegalArgumentException(
                    "Failed rule must have reasonCode"
            );
        }
    }

    /**
     * Factory para uma regra aprovada.
     */
    public static EvaluationRuleResult passed(
            String ruleCode,
            String observedValue,
            String threshold
    ) {
        return new EvaluationRuleResult(
                ruleCode,
                true,
                observedValue,
                threshold,
                null
        );
    }

    /**
     * Factory para uma regra rejeitada.
     */
    public static EvaluationRuleResult failed(
            String ruleCode,
            String observedValue,
            String threshold,
            RejectionReason reasonCode
    ) {
        return new EvaluationRuleResult(
                ruleCode,
                false,
                observedValue,
                threshold,
                Objects.requireNonNull(
                        reasonCode,
                        "reasonCode must not be null"
                )
        );
    }

    private static String requireText(
            String value,
            String message
    ) {
        Objects.requireNonNull(
                value,
                message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    message
            );
        }

        return value;
    }
}