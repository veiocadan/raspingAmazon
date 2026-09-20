package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Regra comercial que verifica se o maior desconto à vista
 * explicitamente observado atende ao mínimo configurado.
 *
 * São considerados somente os meios reconhecidos pela política
 * de desconto à vista:
 *
 * - Pix;
 * - NuPay com Limite Adicional.
 *
 * Parcelamento por cartão não participa desta regra.
 *
 * A regra não calcula desconto pela diferença entre preços e não
 * transforma ausência de desconto em zero.
 */
public final class MinCashDiscountRule {

    private static final String RULE_CODE =
        CommercialFilterRuleCode
            .MIN_CASH_DISCOUNT
            .name();

    private static final String UNAVAILABLE =
        "UNAVAILABLE";

    private final BestCashDiscountSelector selector;

    public MinCashDiscountRule() {
        this(
            new BestCashDiscountSelector()
        );
    }

    /**
     * Construtor explícito para permitir composição e testes sem
     * acoplar a regra à infraestrutura.
     */
    public MinCashDiscountRule(
        BestCashDiscountSelector selector
    ) {
        this.selector =
            Objects.requireNonNull(
                selector,
                "selector must not be null"
            );
    }

    /**
     * Avalia o melhor desconto à vista da oferta.
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
            format(
                profile
                    .minCashDiscountPercentage()
                    .value()
            );

        Optional<CashDiscountObservation> observation =
            selector.select(
                snapshot.paymentConditions()
            );

        if (observation.isEmpty()) {

            return EvaluationRuleResult.failed(
                RULE_CODE,
                UNAVAILABLE,
                threshold,
                RejectionReason.CASH_DISCOUNT_UNAVAILABLE
            );
        }

        CashDiscountObservation best =
            observation.get();

        BigDecimal observedDiscount =
            best
                .discountPercentage()
                .value();

        if (observedDiscount.compareTo(
            profile
                .minCashDiscountPercentage()
                .value()
        ) < 0) {

            return EvaluationRuleResult.failed(
                RULE_CODE,
                best.auditValue(),
                threshold,
                RejectionReason
                    .CASH_DISCOUNT_BELOW_MINIMUM
            );
        }

        return EvaluationRuleResult.passed(
            RULE_CODE,
            best.auditValue(),
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
