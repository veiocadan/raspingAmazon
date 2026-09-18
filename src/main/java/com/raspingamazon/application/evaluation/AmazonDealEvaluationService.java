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
 * Serviço de aplicação responsável por transformar a validação
 * Amazon em uma DealEvaluation.
 *
 * <p>Esta etapa ainda não persiste a avaliação.</p>
 *
 * <p>Score e momentum permanecem nulos porque serão implementados
 * em fases posteriores.</p>
 */
public final class AmazonDealEvaluationService {

    private static final String FILTER_VERSION =
            "AMAZON_SELLER_DELIVERY_V1";

    private final AmazonEligibilityValidator validator;

    public AmazonDealEvaluationService(
            AmazonEligibilityValidator validator
    ) {
        this.validator = Objects.requireNonNull(
                validator,
                "validator must not be null"
        );
    }

    /**
     * Avalia uma oferta e produz sua DealEvaluation.
     *
     * @param offerSnapshot snapshot da oferta a ser avaliada
     * @param sellerType vendedor normalizado
     * @param deliveryType responsável pela entrega normalizado
     * @param evaluatedAt momento da avaliação
     * @return avaliação produzida
     */
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
                FILTER_VERSION,
                null,
                null,
                evaluatedAt
        );
    }
}