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
 * Caso de uso responsável por aplicar a política estrutural Amazon
 * e persistir o resultado agregado.
 */
public final class AmazonDealEvaluationApplicationService {

    /**
     * Esta versão identifica exclusivamente a política de elegibilidade
     * baseada em seller + delivery.
     *
     * <p>Ela não representa filtros configuráveis.</p>
     */
    private static final String ELIGIBILITY_POLICY_VERSION =
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

                        /*
                         * Política estrutural já aplicada.
                         */
                        ELIGIBILITY_POLICY_VERSION,

                        /*
                         * FASE 9 ainda não aplicada.
                         */
                        null,

                        /*
                         * Score ainda não calculado.
                         */
                        null,
                        null,

                        /*
                         * Momentum ainda não calculado.
                         */
                        null,
                        null,

                        evaluatedAt
                );

        return evaluationRepository.save(
                evaluation
        );
    }
}