package com.raspingamazon.domain.filter;

import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;

import java.util.List;
import java.util.Objects;

/**
 * Motor de filtros comerciais configuráveis.
 *
 * <p>A primeira regra é escolhida pela semântica do FilterProfile:</p>
 *
 * <ul>
 *     <li>MIN_CASH_DISCOUNT para o perfil histórico V1;</li>
 *     <li>MIN_BASIS_DISCOUNT para perfis definidos pela ADR-0005.</li>
 * </ul>
 *
 * <p>Rating e quantidade de avaliações permanecem nas posições
 * seguintes e todas as regras são avaliadas sem short-circuit.</p>
 */
public final class CommercialFilterEngine {

    private final MinCashDiscountRule minCashDiscountRule;
    private final MinBasisDiscountRule minBasisDiscountRule;
    private final MinRatingRule minRatingRule;
    private final MinReviewCountRule minReviewCountRule;

    public CommercialFilterEngine() {
        this(
            new MinCashDiscountRule(),
            new MinBasisDiscountRule(),
            new MinRatingRule(),
            new MinReviewCountRule()
        );
    }

    /**
     * Construtor histórico preservado.
     */
    public CommercialFilterEngine(
        MinCashDiscountRule minCashDiscountRule,
        MinRatingRule minRatingRule,
        MinReviewCountRule minReviewCountRule
    ) {
        this(
            minCashDiscountRule,
            new MinBasisDiscountRule(),
            minRatingRule,
            minReviewCountRule
        );
    }

    public CommercialFilterEngine(
        MinCashDiscountRule minCashDiscountRule,
        MinBasisDiscountRule minBasisDiscountRule,
        MinRatingRule minRatingRule,
        MinReviewCountRule minReviewCountRule
    ) {
        this.minCashDiscountRule =
            Objects.requireNonNull(
                minCashDiscountRule,
                "minCashDiscountRule must not be null"
            );

        this.minBasisDiscountRule =
            Objects.requireNonNull(
                minBasisDiscountRule,
                "minBasisDiscountRule must not be null"
            );

        this.minRatingRule =
            Objects.requireNonNull(
                minRatingRule,
                "minRatingRule must not be null"
            );

        this.minReviewCountRule =
            Objects.requireNonNull(
                minReviewCountRule,
                "minReviewCountRule must not be null"
            );
    }

    public List<EvaluationRuleResult> evaluate(
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

        EvaluationRuleResult discountResult =
            evaluateDiscount(
                snapshot,
                profile
            );

        EvaluationRuleResult ratingResult =
            minRatingRule.evaluate(
                snapshot,
                profile
            );

        EvaluationRuleResult reviewCountResult =
            minReviewCountRule.evaluate(
                snapshot,
                profile
            );

        return List.of(
            discountResult,
            ratingResult,
            reviewCountResult
        );
    }

    private EvaluationRuleResult evaluateDiscount(
        OfferSnapshot snapshot,
        FilterProfile profile
    ) {
        if (profile.usesBasisDiscountRule()) {
            return minBasisDiscountRule.evaluate(
                snapshot,
                profile.minBasisDiscountPercentage()
            );
        }

        if (profile.usesCashDiscountRule()) {
            return minCashDiscountRule.evaluate(
                snapshot,
                profile
            );
        }

        throw new IllegalStateException(
            "FilterProfile does not define a supported discount rule"
        );
    }
}
