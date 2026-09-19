package com.raspingamazon.application.evaluation;

import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.validation.AmazonEligibilityResult;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Serviço de aplicação responsável pela avaliação estrutural
 * Amazon de um OfferSnapshot.
 *
 * <p>A política atualmente verifica seller e delivery.
 * O motor de filtros comerciais da FASE 9 continua separado.</p>
 */
public final class AmazonDealEvaluationApplicationService
        implements DealEvaluationProcessingPort {

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

    /**
     * API explícita mantida para consumidores que já fornecem
     * sellerType e deliveryType separadamente.
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
                         * Versão da política estrutural de
                         * seller/delivery.
                         */
                        ELIGIBILITY_POLICY_VERSION,

                        /*
                         * filterProfileVersion.
                         *
                         * A FASE 9 ainda não foi iniciada.
                         */
                        null,

                        /*
                         * Todos os resultados individuais das regras
                         * avaliadas são preservados.
                         */
                        result.ruleResults(),

                        /*
                         * score e scoreVersion ainda não pertencem
                         * a esta fase.
                         */
                        null,
                        null,

                        /*
                         * momentum e momentumVersion ainda não
                         * pertencem a esta fase.
                         */
                        null,
                        null,

                        evaluatedAt
                );

        return evaluationRepository.save(
                evaluation
        );
    }

    /**
     * Implementação da porta utilizada pelo fluxo vertical.
     *
     * <p>SellerType e DeliveryType já fazem parte do OfferSnapshot.
     * Portanto o orquestrador não precisa transportá-los novamente
     * como argumentos separados.</p>
     */
    @Override
    public void evaluateAndPersist(
            OfferSnapshot offerSnapshot,
            OffsetDateTime evaluatedAt
    ) {
        Objects.requireNonNull(
                offerSnapshot,
                "offerSnapshot must not be null"
        );

        evaluate(
                offerSnapshot,
                offerSnapshot.sellerType(),
                offerSnapshot.deliveryType(),
                evaluatedAt
        );
    }
}