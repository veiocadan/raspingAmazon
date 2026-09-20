package com.raspingamazon.application.evaluation;

import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.application.filter.FilterProfileProvider;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.filter.CommercialFilterEngine;
import com.raspingamazon.domain.filter.FilterProfile;
import com.raspingamazon.domain.validation.AmazonEligibilityResult;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Serviço de aplicação responsável por produzir a avaliação completa
 * de uma oferta.
 *
 * A avaliação combina duas etapas conceitualmente distintas:
 *
 * 1. elegibilidade estrutural Amazon;
 * 2. filtros comerciais configuráveis.
 *
 * A separação entre essas etapas é preservada por:
 *
 * - eligibilityPolicyVersion;
 * - filterProfileVersion;
 * - códigos de regra distintos.
 *
 * A ordem das regras também é deliberadamente estável:
 *
 * 1. seller;
 * 2. delivery;
 * 3. desconto à vista;
 * 4. rating;
 * 5. quantidade de avaliações.
 *
 * Todas as regras são avaliadas. Não existe short-circuit.
 */
public final class AmazonDealEvaluationApplicationService
    implements DealEvaluationProcessingPort {

    private static final String ELIGIBILITY_POLICY_VERSION =
        "AMAZON_SELLER_DELIVERY_V1";

    private final AmazonEligibilityValidator eligibilityValidator;

    private final CommercialFilterEngine commercialFilterEngine;

    private final FilterProfileProvider filterProfileProvider;

    private final DealEvaluationRepository evaluationRepository;

    public AmazonDealEvaluationApplicationService(
        AmazonEligibilityValidator eligibilityValidator,
        CommercialFilterEngine commercialFilterEngine,
        FilterProfileProvider filterProfileProvider,
        DealEvaluationRepository evaluationRepository
    ) {
        this.eligibilityValidator =
            Objects.requireNonNull(
                eligibilityValidator,
                "eligibilityValidator must not be null"
            );

        this.commercialFilterEngine =
            Objects.requireNonNull(
                commercialFilterEngine,
                "commercialFilterEngine must not be null"
            );

        this.filterProfileProvider =
            Objects.requireNonNull(
                filterProfileProvider,
                "filterProfileProvider must not be null"
            );

        this.evaluationRepository =
            Objects.requireNonNull(
                evaluationRepository,
                "evaluationRepository must not be null"
            );
    }

    /**
     * Avalia uma oferta utilizando classificações estruturais
     * explicitamente fornecidas.
     *
     * Este método é mantido para consumidores que já possuem
     * SellerType e DeliveryType separadamente.
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

        /*
         * ---------------------------------------------------------
         * 1. ELEGIBILIDADE ESTRUTURAL
         * ---------------------------------------------------------
         */
        AmazonEligibilityResult eligibilityResult =
            eligibilityValidator.validate(
                sellerType,
                deliveryType
            );

        /*
         * ---------------------------------------------------------
         * 2. PERFIL COMERCIAL ATIVO
         * ---------------------------------------------------------
         *
         * O serviço não conhece PostgreSQL.
         *
         * Ele depende somente da porta FilterProfileProvider.
         */
        FilterProfile filterProfile =
            filterProfileProvider.activeProfile();

        /*
         * ---------------------------------------------------------
         * 3. FILTROS COMERCIAIS
         * ---------------------------------------------------------
         */
        List<EvaluationRuleResult> commercialResults =
            commercialFilterEngine.evaluate(
                offerSnapshot,
                filterProfile
            );

        /*
         * ---------------------------------------------------------
         * 4. AGREGAÇÃO DAS REGRAS
         * ---------------------------------------------------------
         *
         * As regras estruturais aparecem primeiro para preservar
         * sua prioridade semântica no rejectionReason agregado.
         */
        List<EvaluationRuleResult> ruleResults =
            combineRuleResults(
                eligibilityResult.ruleResults(),
                commercialResults
            );

        boolean eligible =
            ruleResults.stream()
                .allMatch(
                    EvaluationRuleResult::passed
                );

        RejectionReason rejectionReason =
            firstFailureReason(
                ruleResults
            );

        DealEvaluation evaluation =
            new DealEvaluation(
                null,

                offerSnapshot,

                eligible,

                rejectionReason,

                ELIGIBILITY_POLICY_VERSION,

                filterProfile.version(),

                ruleResults,

                /*
                 * Score pertence à FASE 10.
                 */
                null,
                null,

                /*
                 * Momentum pertence à FASE 11.
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
     * Combina as duas famílias de regras preservando uma ordem
     * determinística.
     */
    private List<EvaluationRuleResult> combineRuleResults(
        List<EvaluationRuleResult> eligibilityResults,
        List<EvaluationRuleResult> commercialResults
    ) {
        Objects.requireNonNull(
            eligibilityResults,
            "eligibilityResults must not be null"
        );

        Objects.requireNonNull(
            commercialResults,
            "commercialResults must not be null"
        );

        List<EvaluationRuleResult> combined =
            new ArrayList<>(
                eligibilityResults.size()
                    + commercialResults.size()
            );

        combined.addAll(
            eligibilityResults
        );

        combined.addAll(
            commercialResults
        );

        return List.copyOf(
            combined
        );
    }

    /**
     * O motivo agregado da rejeição é a razão da primeira regra
     * que falhou.
     *
     * Essa lógica corresponde à própria invariante de DealEvaluation.
     */
    private RejectionReason firstFailureReason(
        List<EvaluationRuleResult> ruleResults
    ) {
        return ruleResults.stream()
            .filter(
                result ->
                    !result.passed()
            )
            .map(
                EvaluationRuleResult::reasonCode
            )
            .findFirst()
            .orElse(
                null
            );
    }

    /**
     * Porta utilizada pelo fluxo vertical.
     *
     * SellerType e DeliveryType já fazem parte do OfferSnapshot.
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
