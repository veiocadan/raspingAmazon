package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.shared.Percentage;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Regra comercial da ADR-0005 que compara o desconto derivado
 * entre basisPrice e effectivePrice com um limite mínimo.
 *
 * <p>Esta regra não utiliza PaymentCondition.discountPercentage.</p>
 *
 * <p>O limite é recebido explicitamente para que a regra não precise
 * reinterpretar o FilterProfile V1 enquanto COMMERCIAL_FILTER_V2
 * ainda não estiver implementado e ativado.</p>
 */
public final class MinBasisDiscountRule {

    private static final String RULE_CODE =
        CommercialFilterRuleCode
            .MIN_BASIS_DISCOUNT
            .name();

    private static final String UNAVAILABLE =
        "UNAVAILABLE";

    private final BasisDiscountCalculator calculator;

    public MinBasisDiscountRule() {

        this(
            new BasisDiscountCalculator()
        );
    }

    public MinBasisDiscountRule(
        BasisDiscountCalculator calculator
    ) {

        this.calculator =
            Objects.requireNonNull(
                calculator,
                "calculator must not be null"
            );
    }

    /**
     * Avalia a redução contra o preço-base.
     *
     * @param snapshot snapshot observado
     * @param minimumBasisDiscount limite percentual mínimo
     * @return resultado auditável da regra
     */
    public EvaluationRuleResult evaluate(
        OfferSnapshot snapshot,
        Percentage minimumBasisDiscount
    ) {

        Objects.requireNonNull(
            snapshot,
            "snapshot must not be null"
        );

        Objects.requireNonNull(
            minimumBasisDiscount,
            "minimumBasisDiscount must not be null"
        );

        String threshold =
            format(
                minimumBasisDiscount.value()
            );

        Optional<BasisDiscountObservation> observation =
            calculator.calculate(
                snapshot
            );

        if (observation.isEmpty()) {

            return EvaluationRuleResult.failed(
                RULE_CODE,
                UNAVAILABLE,
                threshold,
                RejectionReason.BASIS_DISCOUNT_UNAVAILABLE
            );
        }

        BasisDiscountObservation observed =
            observation.get();

        BigDecimal observedDiscount =
            observed
                .discountPercentage()
                .value();

        if (observedDiscount.compareTo(
            minimumBasisDiscount.value()
        ) < 0) {

            return EvaluationRuleResult.failed(
                RULE_CODE,
                observed.auditValue(),
                threshold,
                RejectionReason
                    .BASIS_DISCOUNT_BELOW_MINIMUM
            );
        }

        return EvaluationRuleResult.passed(
            RULE_CODE,
            observed.auditValue(),
            threshold
        );
    }

    private String format(
        BigDecimal value
    ) {

        return value
            .stripTrailingZeros()
            .toPlainString();
    }
}
