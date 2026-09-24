package com.raspingamazon.domain.scoring;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Motor determinístico responsável pelo cálculo do score.
 *
 * <p>O fator de desconto é escolhido pelo ScoreProfile.</p>
 */
public final class ScoreEngine {

    public ScoreResult calculate(
        ScoreProfile profile,
        ScoreInput input
    ) {
        Objects.requireNonNull(
            profile,
            "ScoreProfile must not be null"
        );

        Objects.requireNonNull(
            input,
            "ScoreInput must not be null"
        );

        validateDiscountCompatibility(
            profile,
            input
        );

        ScoreFactorResult soldPercentageFactor =
            calculateSoldPercentageFactor(
                profile,
                input
            );

        ScoreFactorResult discountFactor =
            calculateDiscountFactor(
                profile,
                input
            );

        ScoreFactorResult ratingFactor =
            calculateRatingFactor(
                profile,
                input
            );

        ScoreFactorResult reviewCountFactor =
            calculateReviewCountFactor(
                profile,
                input
            );

        return ScoreResult.fromFactors(
            profile.version(),
            List.of(
                soldPercentageFactor,
                discountFactor,
                ratingFactor,
                reviewCountFactor
            )
        );
    }

    private void validateDiscountCompatibility(
        ScoreProfile profile,
        ScoreInput input
    ) {
        if (profile.usesCashDiscountFactor()
            && !input.usesCashDiscount()) {
            throw new IllegalArgumentException(
                "Cash-discount ScoreProfile requires cash-discount ScoreInput"
            );
        }

        if (profile.usesBasisDiscountFactor()
            && !input.usesBasisDiscount()) {
            throw new IllegalArgumentException(
                "Basis-discount ScoreProfile requires basis-discount ScoreInput"
            );
        }
    }

    private ScoreFactorResult calculateSoldPercentageFactor(
        ScoreProfile profile,
        ScoreInput input
    ) {
        BigDecimal weight =
            profile.weightFor(
                ScoreFactorCode.SOLD_PERCENTAGE
            );

        if (!input.hasSoldPercentage()) {
            return ScoreFactorResult.unavailable(
                ScoreFactorCode.SOLD_PERCENTAGE,
                weight
            );
        }

        BigDecimal normalized =
            ScoreNormalizer.normalizePercentage(
                input.soldPercentage()
            );

        return ScoreFactorResult.available(
            ScoreFactorCode.SOLD_PERCENTAGE,
            input.soldPercentage(),
            normalized,
            weight,
            ScoreNormalizer.calculateContribution(
                normalized,
                weight
            )
        );
    }

    private ScoreFactorResult calculateDiscountFactor(
        ScoreProfile profile,
        ScoreInput input
    ) {
        if (profile.usesBasisDiscountFactor()) {
            return calculateAvailablePercentageFactor(
                ScoreFactorCode.BASIS_DISCOUNT,
                input.basisDiscountPercentage(),
                profile.weightFor(
                    ScoreFactorCode.BASIS_DISCOUNT
                )
            );
        }

        return calculateAvailablePercentageFactor(
            ScoreFactorCode.CASH_DISCOUNT,
            input.cashDiscountPercentage(),
            profile.weightFor(
                ScoreFactorCode.CASH_DISCOUNT
            )
        );
    }

    private ScoreFactorResult calculateAvailablePercentageFactor(
        ScoreFactorCode factorCode,
        BigDecimal rawValue,
        BigDecimal weight
    ) {
        BigDecimal normalized =
            ScoreNormalizer.normalizePercentage(
                rawValue
            );

        return ScoreFactorResult.available(
            factorCode,
            rawValue,
            normalized,
            weight,
            ScoreNormalizer.calculateContribution(
                normalized,
                weight
            )
        );
    }

    private ScoreFactorResult calculateRatingFactor(
        ScoreProfile profile,
        ScoreInput input
    ) {
        BigDecimal weight =
            profile.weightFor(
                ScoreFactorCode.RATING
            );

        BigDecimal normalized =
            ScoreNormalizer.normalizeRating(
                input.rating()
            );

        return ScoreFactorResult.available(
            ScoreFactorCode.RATING,
            input.rating(),
            normalized,
            weight,
            ScoreNormalizer.calculateContribution(
                normalized,
                weight
            )
        );
    }

    private ScoreFactorResult calculateReviewCountFactor(
        ScoreProfile profile,
        ScoreInput input
    ) {
        BigDecimal weight =
            profile.weightFor(
                ScoreFactorCode.REVIEW_COUNT
            );

        BigDecimal normalized =
            ScoreNormalizer.normalizeReviewCount(
                input.reviewCount(),
                profile.reviewCountFullScoreThreshold()
            );

        return ScoreFactorResult.available(
            ScoreFactorCode.REVIEW_COUNT,
            BigDecimal.valueOf(
                input.reviewCount()
            ),
            normalized,
            weight,
            ScoreNormalizer.calculateContribution(
                normalized,
                weight
            )
        );
    }
}
