package com.raspingamazon.application.evaluation;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.validation.AmazonEligibilityResult;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;

import java.time.OffsetDateTime;
import java.util.Objects;

public final class AmazonDealEvaluationApplicationService {

    private static final String FILTER_VERSION =
            "AMAZON_SELLER_DELIVERY_V1";

    private final AmazonEligibilityValidator eligibilityValidator;
    private final DealEvaluationRepository evaluationRepository;

    public AmazonDealEvaluationApplicationService(
            AmazonEligibilityValidator eligibilityValidator,
            DealEvaluationRepository evaluationRepository
    ) {
        this.eligibilityValidator =
                Objects.requireNonNull(
                        eligibilityValidator,
                        "eligibilityValidator must not be null"
                );

        this.evaluationRepository =
                Objects.requireNonNull(
                        evaluationRepository,
                        "evaluationRepository must not be null"
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
                evaluatedAt,
                "evaluatedAt must not be null"
        );

        AmazonEligibilityResult result =
                eligibilityValidator.validate(
                        sellerType,
                        deliveryType
                );

        DealEvaluation evaluation =
                new DealEvaluation(
                        null,
                        offerSnapshot,
                        result.eligible(),
                        result.rejectionReason(),
                        FILTER_VERSION,
                        null,
                        null,
                        evaluatedAt
                );

        return evaluationRepository.save(evaluation);
    }
}