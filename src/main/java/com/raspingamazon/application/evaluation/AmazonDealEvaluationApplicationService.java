package com.raspingamazon.application.evaluation;

import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.application.filter.FilterProfileProvider;
import com.raspingamazon.application.scoring.ScoreProfileProvider;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.filter.BestCashDiscountSelector;
import com.raspingamazon.domain.filter.CashDiscountObservation;
import com.raspingamazon.domain.filter.CommercialFilterEngine;
import com.raspingamazon.domain.filter.FilterProfile;
import com.raspingamazon.domain.scoring.ScoreEngine;
import com.raspingamazon.domain.scoring.ScoreInput;
import com.raspingamazon.domain.scoring.ScoreProfile;
import com.raspingamazon.domain.scoring.ScoreResult;
import com.raspingamazon.domain.validation.AmazonEligibilityResult;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Serviço de aplicação responsável por produzir a avaliação completa
 * de uma oferta.
 *
 * <p>A avaliação combina três etapas conceitualmente distintas:</p>
 *
 * <ol>
 *     <li>elegibilidade estrutural Amazon;</li>
 *     <li>filtros comerciais configuráveis;</li>
 *     <li>score para ofertas aprovadas.</li>
 * </ol>
 *
 * <p>A separação entre essas etapas é preservada por:</p>
 *
 * <ul>
 *     <li>eligibilityPolicyVersion;</li>
 *     <li>filterProfileVersion;</li>
 *     <li>scoreVersion;</li>
 *     <li>códigos de regra distintos;</li>
 *     <li>fatores auditáveis do score.</li>
 * </ul>
 *
 * <p>A ordem das regras eliminatórias é deliberadamente estável:</p>
 *
 * <ol>
 *     <li>seller;</li>
 *     <li>delivery;</li>
 *     <li>desconto à vista;</li>
 *     <li>rating;</li>
 *     <li>quantidade de avaliações.</li>
 * </ol>
 *
 * <p>Todas as regras eliminatórias são avaliadas. Não existe
 * short-circuit entre elas.</p>
 *
 * <p>O score, entretanto, somente é calculado depois que todas
 * essas regras tiverem sido aprovadas.</p>
 */
public final class AmazonDealEvaluationApplicationService
    implements DealEvaluationProcessingPort {

    private static final String ELIGIBILITY_POLICY_VERSION =
        "AMAZON_SELLER_DELIVERY_V1";

    private final AmazonEligibilityValidator eligibilityValidator;

    private final CommercialFilterEngine commercialFilterEngine;

    private final FilterProfileProvider filterProfileProvider;

    private final ScoreProfileProvider scoreProfileProvider;

    private final ScoreEngine scoreEngine;

    private final BestCashDiscountSelector bestCashDiscountSelector;

    private final DealEvaluationRepository evaluationRepository;

    public AmazonDealEvaluationApplicationService(
        AmazonEligibilityValidator eligibilityValidator,
        CommercialFilterEngine commercialFilterEngine,
        FilterProfileProvider filterProfileProvider,
        ScoreProfileProvider scoreProfileProvider,
        ScoreEngine scoreEngine,
        BestCashDiscountSelector bestCashDiscountSelector,
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

        this.scoreProfileProvider =
            Objects.requireNonNull(
                scoreProfileProvider,
                "scoreProfileProvider must not be null"
            );

        this.scoreEngine =
            Objects.requireNonNull(
                scoreEngine,
                "scoreEngine must not be null"
            );

        this.bestCashDiscountSelector =
            Objects.requireNonNull(
                bestCashDiscountSelector,
                "bestCashDiscountSelector must not be null"
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
     * <p>Este método é mantido para consumidores que já possuem
     * SellerType e DeliveryType separadamente.</p>
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

        /*
         * ---------------------------------------------------------
         * 5. SCORE
         * ---------------------------------------------------------
         *
         * Score somente existe quando todas as etapas eliminatórias
         * anteriores foram aprovadas.
         */
        ScoreResult scoreResult =
            null;

        if (eligible) {

            ScoreProfile scoreProfile =
                scoreProfileProvider.activeProfile();

            ScoreInput scoreInput =
                createScoreInput(
                    offerSnapshot
                );

            scoreResult =
                scoreEngine.calculate(
                    scoreProfile,
                    scoreInput
                );
        }

        /*
         * ---------------------------------------------------------
         * 6. AVALIAÇÃO AGREGADA
         * ---------------------------------------------------------
         */
        DealEvaluation evaluation;

        if (scoreResult == null) {

            evaluation =
                new DealEvaluation(
                    null,
                    offerSnapshot,
                    false,
                    rejectionReason,
                    ELIGIBILITY_POLICY_VERSION,
                    filterProfile.version(),
                    ruleResults,
                    null,
                    null,
                    null,
                    null,
                    evaluatedAt
                );

        } else {

            evaluation =
                new DealEvaluation(
                    null,
                    offerSnapshot,
                    true,
                    null,
                    ELIGIBILITY_POLICY_VERSION,
                    filterProfile.version(),
                    ruleResults,
                    scoreResult.score(),
                    scoreResult.version(),
                    scoreResult.factors(),
                    null,
                    null,
                    evaluatedAt
                );
        }

        return evaluationRepository.save(
            evaluation
        );
    }

    /**
     * Constrói os fatos necessários ao motor de score a partir
     * da oferta já aprovada pelos filtros comerciais.
     *
     * <p>Esta conversão ocorre somente depois da aprovação dos filtros.
     * Consequentemente, desconto à vista, rating e reviewCount devem
     * estar disponíveis.</p>
     *
     * <p>soldPercentage continua opcional por definição do SCORE_V1.</p>
     */
    private ScoreInput createScoreInput(
        OfferSnapshot offerSnapshot
    ) {

        CashDiscountObservation cashDiscount =
            bestCashDiscountSelector
                .select(
                    offerSnapshot.paymentConditions()
                )
                .orElseThrow(
                    () -> new IllegalStateException(
                        "Eligible offer must have a recognized cash discount"
                    )
                );

        if (offerSnapshot.rating() == null) {
            throw new IllegalStateException(
                "Eligible offer must have rating"
            );
        }

        if (offerSnapshot.reviewCount() == null) {
            throw new IllegalStateException(
                "Eligible offer must have reviewCount"
            );
        }

        BigDecimal soldPercentage =
            offerSnapshot.soldPercentage() == null
                ? null
                : offerSnapshot
                .soldPercentage()
                .value();

        /*
         * OfferSnapshot mantém rating como Double por contrato
         * histórico.
         *
         * O domínio de scoring utiliza BigDecimal.
         *
         * BigDecimal.valueOf é usado deliberadamente para evitar
         * a representação binária indesejada produzida por
         * new BigDecimal(double).
         */
        BigDecimal rating =
            BigDecimal.valueOf(
                offerSnapshot.rating()
            );

        return new ScoreInput(
            soldPercentage,
            cashDiscount
                .discountPercentage()
                .value(),
            rating,
            offerSnapshot.reviewCount()
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
     * <p>SellerType e DeliveryType já fazem parte do OfferSnapshot.</p>
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
