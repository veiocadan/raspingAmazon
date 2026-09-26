package com.raspingamazon.application.operation.evaluation;

import java.util.Objects;

/**
 * Resultado persistido de uma regra individual exibido pela
 * interface operacional.
 *
 * <p>Este read model não executa nem recalcula a regra.</p>
 */
public record OperationalEvaluationRuleResult(
    int ruleOrder,
    String ruleCode,
    boolean passed,
    String observedValue,
    String thresholdValue,
    String reasonCode
) {

    public OperationalEvaluationRuleResult {

        if (ruleOrder < 0) {
            throw new IllegalArgumentException(
                "OperationalEvaluationRuleResult ruleOrder "
                    + "must not be negative"
            );
        }

        ruleCode =
            requireText(
                ruleCode,
                "OperationalEvaluationRuleResult "
                    + "ruleCode must not be blank"
            );

        observedValue =
            requireText(
                observedValue,
                "OperationalEvaluationRuleResult "
                    + "observedValue must not be blank"
            );

        thresholdValue =
            requireText(
                thresholdValue,
                "OperationalEvaluationRuleResult "
                    + "thresholdValue must not be blank"
            );

        reasonCode =
            optionalText(
                reasonCode,
                "OperationalEvaluationRuleResult "
                    + "reasonCode must not be blank"
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

    private static String optionalText(
        String value,
        String message
    ) {

        if (value == null) {
            return null;
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
