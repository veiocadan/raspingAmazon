package com.raspingamazon.application.evaluation;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.validation.AmazonEligibilityResult;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Serviço não persistente da avaliação Amazon.
 *
 * <p>Permanece temporariamente por compatibilidade enquanto
 * a consolidação dos casos de uso não é concluída.</p>
 */
public final class AmazonDealEvaluationService {

    private static final String ELIGIBILITY_POLICY_VERSION =
            "AMAZON_SELLER_DELIVERY_V1";

    private final AmazonEligibilityValidator validator;

    public AmazonDealEvaluationService(
            AmazonEligibilityValidator validator
    ) {
        this.validator =
                Objects.requireNonNull(
                        validator,
                        "validator must not be null"
                );
    }

    public DealEvaluation evaluate(
            OfferSnapshot offerSnapshot,
            SellerType sellerType,
            DeliveryType deliveryType,
            OffsetDateTime evaluatedAt
    ) {
        Objects.requireNonNull(
                offerSnapshot,
                "offerSnapshot must not be null"
        );

        Objects.requireNonNull(
                sellerType,
                "sellerType must not be null"
        );

        Objects.requireNonNull(
                deliveryType,
                "deliveryType must not be null"
        );

        Objects.requireNonNull(
                evaluatedAt,
                "evaluatedAt must not be null"
        );

        AmazonEligibilityResult result =
                validator.validate(
                        sellerType,
                        deliveryType
                );

        return new DealEvaluation(
                null,
                offerSnapshot,
                result.eligible(),
                result.rejectionReason(),
                ELIGIBILITY_POLICY_VERSION,
                null,
                result.ruleResults(),
                null,
                null,
                null,
                null,
                evaluatedAt
        );
    }
}