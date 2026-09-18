package com.raspingamazon.domain.validation;

import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;

import java.util.List;
import java.util.Objects;

/**
 * Resultado agregado da política de elegibilidade Amazon.
 *
 * <p>Além da decisão final, preserva os resultados individuais
 * das regras que participaram da decisão.</p>
 */
public record AmazonEligibilityResult(
        boolean eligible,
        RejectionReason rejectionReason,
        List<EvaluationRuleResult> ruleResults
) {

    public AmazonEligibilityResult {

        Objects.requireNonNull(
                ruleResults,
                "ruleResults must not be null"
        );

        /*
         * Fazemos uma cópia defensiva para impedir que alguém altere
         * os resultados depois que a avaliação foi produzida.
         */
        ruleResults =
                List.copyOf(
                        ruleResults
                );

        if (ruleResults.isEmpty()) {
            throw new IllegalArgumentException(
                    "ruleResults must not be empty"
            );
        }

        if (eligible && rejectionReason != null) {
            throw new IllegalArgumentException(
                    "Eligible result must not have a rejection reason"
            );
        }

        if (!eligible && rejectionReason == null) {
            throw new IllegalArgumentException(
                    "Ineligible result must have a rejection reason"
            );
        }

        /*
         * A decisão agregada também precisa ser coerente com
         * os resultados das regras.
         */
        boolean allRulesPassed =
                ruleResults.stream()
                        .allMatch(
                                EvaluationRuleResult::passed
                        );

        if (eligible != allRulesPassed) {
            throw new IllegalArgumentException(
                    "Eligibility must match the aggregated rule results"
            );
        }
    }

    /**
     * Cria um resultado elegível.
     */
    public static AmazonEligibilityResult accepted(
            List<EvaluationRuleResult> ruleResults
    ) {
        return new AmazonEligibilityResult(
                true,
                null,
                ruleResults
        );
    }

    /**
     * Cria um resultado rejeitado.
     *
     * <p>rejectionReason continua existindo como o motivo principal
     * agregado por compatibilidade e consulta rápida.</p>
     */
    public static AmazonEligibilityResult rejected(
            RejectionReason rejectionReason,
            List<EvaluationRuleResult> ruleResults
    ) {
        return new AmazonEligibilityResult(
                false,
                Objects.requireNonNull(
                        rejectionReason,
                        "rejectionReason must not be null"
                ),
                ruleResults
        );
    }
}