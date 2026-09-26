package com.raspingamazon.application.operation.evaluation;

import com.raspingamazon.domain.shared.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Read model detalhado de uma DealEvaluation para uso operacional.
 *
 * <p>A parte resumida é reutilizada de DealEvaluationSummary.
 * Os demais campos acrescentam contexto da oferta e explicabilidade
 * persistida.</p>
 *
 * <p>Este objeto não representa um novo agregado de domínio e não
 * deve ser utilizado pelo pipeline automático de decisão.</p>
 */
public record DealEvaluationDetail(
    DealEvaluationSummary summary,
    String productUrl,
    Money basisPrice,
    Money previousPrice,
    BigDecimal soldPercentage,
    Double rating,
    Long reviewCount,
    String sellerName,
    String deliveryProvider,
    String source,
    String eligibilityPolicyVersion,
    String filterProfileVersion,
    String scoreVersion,
    List<OperationalEvaluationRuleResult> ruleResults,
    List<OperationalScoreFactorResult> scoreFactors,
    String momentumVersion,
    OperationalMomentumAudit momentumAudit
) {

    public DealEvaluationDetail {

        Objects.requireNonNull(
            summary,
            "DealEvaluationDetail summary must not be null"
        );

        productUrl =
            requireText(
                productUrl,
                "DealEvaluationDetail productUrl must not be blank"
            );

        sellerName =
            requireText(
                sellerName,
                "DealEvaluationDetail sellerName must not be blank"
            );

        deliveryProvider =
            requireText(
                deliveryProvider,
                "DealEvaluationDetail deliveryProvider must not be blank"
            );

        source =
            requireText(
                source,
                "DealEvaluationDetail source must not be blank"
            );

        eligibilityPolicyVersion =
            requireText(
                eligibilityPolicyVersion,
                "DealEvaluationDetail "
                    + "eligibilityPolicyVersion must not be blank"
            );

        filterProfileVersion =
            optionalText(
                filterProfileVersion,
                "DealEvaluationDetail "
                    + "filterProfileVersion must not be blank"
            );

        scoreVersion =
            optionalText(
                scoreVersion,
                "DealEvaluationDetail scoreVersion must not be blank"
            );

        momentumVersion =
            optionalText(
                momentumVersion,
                "DealEvaluationDetail momentumVersion must not be blank"
            );

        Objects.requireNonNull(
            ruleResults,
            "DealEvaluationDetail ruleResults must not be null"
        );

        Objects.requireNonNull(
            scoreFactors,
            "DealEvaluationDetail scoreFactors must not be null"
        );

        ruleResults =
            List.copyOf(
                ruleResults
            );

        scoreFactors =
            List.copyOf(
                scoreFactors
            );

        validateRuleOrdering(
            ruleResults
        );

        validateFactorOrdering(
            scoreFactors
        );
    }

    private static void validateRuleOrdering(
        List<OperationalEvaluationRuleResult> results
    ) {

        for (int index = 1;
             index < results.size();
             index++) {

            int previousOrder =
                results.get(
                    index - 1
                ).ruleOrder();

            int currentOrder =
                results.get(
                    index
                ).ruleOrder();

            if (previousOrder >= currentOrder) {

                throw new IllegalArgumentException(
                    "DealEvaluationDetail ruleResults "
                        + "must be ordered by ruleOrder ASC"
                );
            }
        }
    }

    private static void validateFactorOrdering(
        List<OperationalScoreFactorResult> factors
    ) {

        for (int index = 1;
             index < factors.size();
             index++) {

            int previousOrder =
                factors.get(
                    index - 1
                ).factorOrder();

            int currentOrder =
                factors.get(
                    index
                ).factorOrder();

            if (previousOrder >= currentOrder) {

                throw new IllegalArgumentException(
                    "DealEvaluationDetail scoreFactors "
                        + "must be ordered by factorOrder ASC"
                );
            }
        }
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
