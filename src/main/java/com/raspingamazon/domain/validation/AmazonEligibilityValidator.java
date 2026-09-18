package com.raspingamazon.domain.validation;

import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;

import java.util.List;
import java.util.Objects;

/**
 * Política estrutural de elegibilidade Amazon.
 *
 * <p>A oferta somente é elegível quando:</p>
 *
 * <ul>
 *     <li>o vendedor é Amazon;</li>
 *     <li>a entrega é Amazon.</li>
 * </ul>
 *
 * <p>A política permanece fail-closed: UNKNOWN e THIRD_PARTY
 * são rejeitados.</p>
 *
 * <p>Diferentemente da implementação anterior, todas as regras são
 * avaliadas. Isso permite explicar completamente uma rejeição.</p>
 */
public final class AmazonEligibilityValidator {

    private static final String SELLER_RULE =
            "SELLER_IS_AMAZON";

    private static final String DELIVERY_RULE =
            "DELIVERY_IS_AMAZON";

    private static final String REQUIRED_VALUE =
            "AMAZON";

    /**
     * Avalia seller e delivery independentemente.
     */
    public AmazonEligibilityResult validate(
            SellerType sellerType,
            DeliveryType deliveryType
    ) {
        Objects.requireNonNull(
                sellerType,
                "sellerType must not be null"
        );

        Objects.requireNonNull(
                deliveryType,
                "deliveryType must not be null"
        );

        EvaluationRuleResult sellerResult =
                evaluateSeller(
                        sellerType
                );

        EvaluationRuleResult deliveryResult =
                evaluateDelivery(
                        deliveryType
                );

        List<EvaluationRuleResult> ruleResults =
                List.of(
                        sellerResult,
                        deliveryResult
                );

        /*
         * Elegibilidade somente existe quando TODAS as regras passam.
         */
        if (sellerResult.passed()
                && deliveryResult.passed()) {

            return AmazonEligibilityResult.accepted(
                    ruleResults
            );
        }

        /*
         * rejectionReason continua representando o motivo principal.
         *
         * Mantemos seller como primeira prioridade para preservar
         * deterministicamente o comportamento histórico da FASE 8.
         *
         * A diferença é que agora delivery também é avaliado e
         * preservado em ruleResults.
         */
        RejectionReason primaryReason;

        if (!sellerResult.passed()) {
            primaryReason =
                    sellerResult.reasonCode();
        } else {
            primaryReason =
                    deliveryResult.reasonCode();
        }

        return AmazonEligibilityResult.rejected(
                primaryReason,
                ruleResults
        );
    }

    /**
     * Avalia exclusivamente a regra de seller.
     */
    private EvaluationRuleResult evaluateSeller(
            SellerType sellerType
    ) {
        return switch (sellerType) {

            case AMAZON ->
                    EvaluationRuleResult.passed(
                            SELLER_RULE,
                            sellerType.name(),
                            REQUIRED_VALUE
                    );

            case THIRD_PARTY ->
                    EvaluationRuleResult.failed(
                            SELLER_RULE,
                            sellerType.name(),
                            REQUIRED_VALUE,
                            RejectionReason.SELLER_THIRD_PARTY
                    );

            case UNKNOWN ->
                    EvaluationRuleResult.failed(
                            SELLER_RULE,
                            sellerType.name(),
                            REQUIRED_VALUE,
                            RejectionReason.SELLER_UNKNOWN
                    );
        };
    }

    /**
     * Avalia exclusivamente a regra de delivery.
     */
    private EvaluationRuleResult evaluateDelivery(
            DeliveryType deliveryType
    ) {
        return switch (deliveryType) {

            case AMAZON ->
                    EvaluationRuleResult.passed(
                            DELIVERY_RULE,
                            deliveryType.name(),
                            REQUIRED_VALUE
                    );

            case THIRD_PARTY ->
                    EvaluationRuleResult.failed(
                            DELIVERY_RULE,
                            deliveryType.name(),
                            REQUIRED_VALUE,
                            RejectionReason.DELIVERY_THIRD_PARTY
                    );

            case UNKNOWN ->
                    EvaluationRuleResult.failed(
                            DELIVERY_RULE,
                            deliveryType.name(),
                            REQUIRED_VALUE,
                            RejectionReason.DELIVERY_UNKNOWN
                    );
        };
    }
}