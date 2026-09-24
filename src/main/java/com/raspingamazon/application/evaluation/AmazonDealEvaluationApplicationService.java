package com.raspingamazon.application.evaluation;

import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.application.filter.FilterProfileProvider;
import com.raspingamazon.application.momentum.MomentumAuditRepository;
import com.raspingamazon.application.momentum.MomentumCalculation;
import com.raspingamazon.application.momentum.MomentumCalculationService;
import com.raspingamazon.application.scoring.ScoreProfileProvider;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.evaluation.RejectionReason;
import com.raspingamazon.domain.filter.BasisDiscountCalculator;
import com.raspingamazon.domain.filter.BasisDiscountObservation;
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
 * <p>O ScoreInput é construído de acordo com a semântica do
 * ScoreProfile ativo:</p>
 *
 * <ul>
 *     <li>SCORE_V1 continua utilizando CASH_DISCOUNT;</li>
 *     <li>SCORE_V2 utiliza BASIS_DISCOUNT.</li>
 * </ul>
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
    private final BasisDiscountCalculator basisDiscountCalculator;
    private final MomentumCalculationService momentumCalculationService;
    private final DealEvaluationRepository evaluationRepository;
    private final MomentumAuditRepository momentumAuditRepository;

    /**
     * Construtor histórico preservado.
     */
    public AmazonDealEvaluationApplicationService(
        AmazonEligibilityValidator eligibilityValidator,
        CommercialFilterEngine commercialFilterEngine,
        FilterProfileProvider filterProfileProvider,
        ScoreProfileProvider scoreProfileProvider,
        ScoreEngine scoreEngine,
        BestCashDiscountSelector bestCashDiscountSelector,
        MomentumCalculationService momentumCalculationService,
        DealEvaluationRepository evaluationRepository,
        MomentumAuditRepository momentumAuditRepository
    ) {
        this(
            eligibilityValidator,
            commercialFilterEngine,
            filterProfileProvider,
            scoreProfileProvider,
            scoreEngine,
            bestCashDiscountSelector,
            new BasisDiscountCalculator(),
            momentumCalculationService,
            evaluationRepository,
            momentumAuditRepository
        );
    }

    public AmazonDealEvaluationApplicationService(
        AmazonEligibilityValidator eligibilityValidator,
        CommercialFilterEngine commercialFilterEngine,
        FilterProfileProvider filterProfileProvider,
        ScoreProfileProvider scoreProfileProvider,
        ScoreEngine scoreEngine,
        BestCashDiscountSelector bestCashDiscountSelector,
        BasisDiscountCalculator basisDiscountCalculator,
        MomentumCalculationService momentumCalculationService,
        DealEvaluationRepository evaluationRepository,
        MomentumAuditRepository momentumAuditRepository
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

        this.basisDiscountCalculator =
            Objects.requireNonNull(
                basisDiscountCalculator,
                "basisDiscountCalculator must not be null"
            );

        this.momentumCalculationService =
            Objects.requireNonNull(
                momentumCalculationService,
                "momentumCalculationService must not be null"
            );

        this.evaluationRepository =
            Objects.requireNonNull(
                evaluationRepository,
                "evaluationRepository must not be null"
            );

        this.momentumAuditRepository =
            Objects.requireNonNull(
                momentumAuditRepository,
                "momentumAuditRepository must not be null"
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

        AmazonEligibilityResult eligibilityResult =
            eligibilityValidator.validate(
                sellerType,
                deliveryType
            );

        FilterProfile filterProfile =
            filterProfileProvider.activeProfile();

        List<EvaluationRuleResult> commercialResults =
            commercialFilterEngine.evaluate(
                offerSnapshot,
                filterProfile
            );

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

        ScoreResult scoreResult =
            null;

        if (eligible) {
            ScoreProfile scoreProfile =
                scoreProfileProvider.activeProfile();

            ScoreInput scoreInput =
                createScoreInput(
                    offerSnapshot,
                    scoreProfile
                );

            scoreResult =
                scoreEngine.calculate(
                    scoreProfile,
                    scoreInput
                );
        }

        MomentumCalculation momentumCalculation =
            momentumCalculationService.calculate(
                offerSnapshot
            );

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
                    momentumCalculation
                        .dealEvaluationMomentum(),
                    momentumCalculation
                        .dealEvaluationMomentumVersion(),
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
                    momentumCalculation
                        .dealEvaluationMomentum(),
                    momentumCalculation
                        .dealEvaluationMomentumVersion(),
                    evaluatedAt
                );
        }

        DealEvaluation persistedEvaluation =
            evaluationRepository.save(
                evaluation
            );

        Long evaluationId =
            persistedEvaluation.id();

        if (evaluationId == null
            || evaluationId <= 0) {
            throw new IllegalStateException(
                "Persisted DealEvaluation must have a positive id before momentum audit"
            );
        }

        momentumAuditRepository.save(
            momentumCalculation.toAudit(
                evaluationId
            )
        );

        return persistedEvaluation;
    }

    private ScoreInput createScoreInput(
        OfferSnapshot offerSnapshot,
        ScoreProfile scoreProfile
    ) {
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

        BigDecimal rating =
            BigDecimal.valueOf(
                offerSnapshot.rating()
            );

        if (scoreProfile.usesBasisDiscountFactor()) {
            BasisDiscountObservation basisDiscount =
                basisDiscountCalculator
                    .calculate(
                        offerSnapshot
                    )
                    .orElseThrow(
                        () -> new IllegalStateException(
                            "Eligible offer must have a calculable basis discount for BASIS_DISCOUNT score"
                        )
                    );

            return ScoreInput.forBasisDiscount(
                soldPercentage,
                basisDiscount
                    .discountPercentage()
                    .value(),
                rating,
                offerSnapshot.reviewCount()
            );
        }

        CashDiscountObservation cashDiscount =
            bestCashDiscountSelector
                .select(
                    offerSnapshot.paymentConditions()
                )
                .orElseThrow(
                    () -> new IllegalStateException(
                        "Eligible offer must have a recognized cash discount for CASH_DISCOUNT score"
                    )
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
