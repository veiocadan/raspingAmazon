package com.raspingamazon.domain.scoring;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Motor determinístico responsável pelo cálculo do score.
 *
 * <p>O ScoreEngine combina:</p>
 *
 * <ul>
 *     <li>fatos observados em ScoreInput;</li>
 *     <li>pesos e parâmetros versionados em ScoreProfile;</li>
 *     <li>normalizações matemáticas de ScoreNormalizer;</li>
 * </ul>
 *
 * <p>O resultado contém tanto a pontuação agregada quanto todos
 * os fatores necessários para explicar e reproduzir o cálculo.</p>
 *
 * <p>Esta classe não conhece persistência, SQL, elegibilidade,
 * filtros comerciais ou ranking. O motor pressupõe que a camada
 * de aplicação somente o invoque para ofertas que já tenham
 * passado pelas etapas anteriores.</p>
 */
public final class ScoreEngine {

    /**
     * Calcula o score de forma completamente determinística.
     */
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

        ScoreFactorResult soldPercentageFactor =
            calculateSoldPercentageFactor(
                profile,
                input
            );

        ScoreFactorResult cashDiscountFactor =
            calculateCashDiscountFactor(
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
                cashDiscountFactor,
                ratingFactor,
                reviewCountFactor
            )
        );
    }

    private ScoreFactorResult calculateSoldPercentageFactor(
        ScoreProfile profile,
        ScoreInput input
    ) {
        BigDecimal weight = profile.weightFor(
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

    private ScoreFactorResult calculateCashDiscountFactor(
        ScoreProfile profile,
        ScoreInput input
    ) {
        BigDecimal weight = profile.weightFor(
            ScoreFactorCode.CASH_DISCOUNT
        );

        BigDecimal normalized =
            ScoreNormalizer.normalizePercentage(
                input.cashDiscountPercentage()
            );

        return ScoreFactorResult.available(
            ScoreFactorCode.CASH_DISCOUNT,
            input.cashDiscountPercentage(),
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
        BigDecimal weight = profile.weightFor(
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
        BigDecimal weight = profile.weightFor(
            ScoreFactorCode.REVIEW_COUNT
        );

        BigDecimal normalized =
            ScoreNormalizer.normalizeReviewCount(
                input.reviewCount(),
                profile.reviewCountFullScoreThreshold()
            );

        return ScoreFactorResult.available(
            ScoreFactorCode.REVIEW_COUNT,
            BigDecimal.valueOf(input.reviewCount()),
            normalized,
            weight,
            ScoreNormalizer.calculateContribution(
                normalized,
                weight
            )
        );
    }
}
