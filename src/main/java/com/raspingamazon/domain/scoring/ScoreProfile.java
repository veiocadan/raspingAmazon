package com.raspingamazon.domain.scoring;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Configuração versionada utilizada pelo motor de score.
 *
 * <p>Um perfil utiliza exatamente um fator de desconto:</p>
 *
 * <ul>
 *     <li>CASH_DISCOUNT para SCORE_V1;</li>
 *     <li>BASIS_DISCOUNT para SCORE_V2.</li>
 * </ul>
 */
public record ScoreProfile(
    String version,
    BigDecimal soldPercentageWeight,
    BigDecimal cashDiscountWeight,
    BigDecimal basisDiscountWeight,
    BigDecimal ratingWeight,
    BigDecimal reviewCountWeight,
    long reviewCountFullScoreThreshold
) {

    private static final BigDecimal MIN_WEIGHT =
        BigDecimal.ZERO;

    private static final BigDecimal MAX_WEIGHT =
        new BigDecimal("100");

    private static final BigDecimal MAX_TOTAL_WEIGHT =
        new BigDecimal("100");

    /**
     * Construtor histórico do SCORE_V1.
     */
    public ScoreProfile(
        String version,
        BigDecimal soldPercentageWeight,
        BigDecimal cashDiscountWeight,
        BigDecimal ratingWeight,
        BigDecimal reviewCountWeight,
        long reviewCountFullScoreThreshold
    ) {
        this(
            version,
            soldPercentageWeight,
            requireLegacyCashDiscountWeight(
                cashDiscountWeight
            ),
            null,
            ratingWeight,
            reviewCountWeight,
            reviewCountFullScoreThreshold
        );
    }

    public ScoreProfile {

        version =
            requireText(
                version,
                "ScoreProfile version must not be blank"
            );

        soldPercentageWeight =
            requireWeight(
                soldPercentageWeight,
                "ScoreProfile soldPercentageWeight must be between 0 and 100"
            );

        ratingWeight =
            requireWeight(
                ratingWeight,
                "ScoreProfile ratingWeight must be between 0 and 100"
            );

        reviewCountWeight =
            requireWeight(
                reviewCountWeight,
                "ScoreProfile reviewCountWeight must be between 0 and 100"
            );

        boolean hasCash =
            cashDiscountWeight != null;

        boolean hasBasis =
            basisDiscountWeight != null;

        if (hasCash == hasBasis) {
            throw new IllegalArgumentException(
                "ScoreProfile must define exactly one discount weight"
            );
        }

        if (cashDiscountWeight != null) {
            cashDiscountWeight =
                requireWeight(
                    cashDiscountWeight,
                    "ScoreProfile cashDiscountWeight must be between 0 and 100"
                );
        }

        if (basisDiscountWeight != null) {
            basisDiscountWeight =
                requireWeight(
                    basisDiscountWeight,
                    "ScoreProfile basisDiscountWeight must be between 0 and 100"
                );
        }

        if (reviewCountFullScoreThreshold <= 0) {
            throw new IllegalArgumentException(
                "ScoreProfile reviewCountFullScoreThreshold must be positive"
            );
        }

        BigDecimal totalWeight =
            soldPercentageWeight
                .add(
                    activeDiscountWeight(
                        cashDiscountWeight,
                        basisDiscountWeight
                    )
                )
                .add(ratingWeight)
                .add(reviewCountWeight);

        if (totalWeight.compareTo(MAX_TOTAL_WEIGHT) > 0) {
            throw new IllegalArgumentException(
                "ScoreProfile total active weight must not exceed 100"
            );
        }
    }

    public static ScoreProfile forBasisDiscount(
        String version,
        BigDecimal soldPercentageWeight,
        BigDecimal basisDiscountWeight,
        BigDecimal ratingWeight,
        BigDecimal reviewCountWeight,
        long reviewCountFullScoreThreshold
    ) {
        return new ScoreProfile(
            version,
            soldPercentageWeight,
            null,
            Objects.requireNonNull(
                basisDiscountWeight,
                "ScoreProfile basisDiscountWeight must not be null"
            ),
            ratingWeight,
            reviewCountWeight,
            reviewCountFullScoreThreshold
        );
    }

    public boolean usesCashDiscountFactor() {
        return cashDiscountWeight != null;
    }

    public boolean usesBasisDiscountFactor() {
        return basisDiscountWeight != null;
    }

    public BigDecimal weightFor(
        ScoreFactorCode factorCode
    ) {
        Objects.requireNonNull(
            factorCode,
            "ScoreFactorCode must not be null"
        );

        return switch (factorCode) {
            case SOLD_PERCENTAGE ->
                soldPercentageWeight;
            case CASH_DISCOUNT ->
                cashDiscountWeight == null
                    ? BigDecimal.ZERO
                    : cashDiscountWeight;
            case BASIS_DISCOUNT ->
                basisDiscountWeight == null
                    ? BigDecimal.ZERO
                    : basisDiscountWeight;
            case RATING ->
                ratingWeight;
            case REVIEW_COUNT ->
                reviewCountWeight;
        };
    }

    public BigDecimal totalActiveWeight() {
        return soldPercentageWeight
            .add(
                activeDiscountWeight(
                    cashDiscountWeight,
                    basisDiscountWeight
                )
            )
            .add(ratingWeight)
            .add(reviewCountWeight);
    }

    private static BigDecimal activeDiscountWeight(
        BigDecimal cashDiscountWeight,
        BigDecimal basisDiscountWeight
    ) {
        return cashDiscountWeight != null
            ? cashDiscountWeight
            : basisDiscountWeight;
    }

    private static BigDecimal requireLegacyCashDiscountWeight(
        BigDecimal value
    ) {
        return Objects.requireNonNull(
            value,
            "ScoreProfile cashDiscountWeight must not be null"
        );
    }

    private static BigDecimal requireWeight(
        BigDecimal value,
        String message
    ) {
        Objects.requireNonNull(value, message);

        if (value.compareTo(MIN_WEIGHT) < 0
            || value.compareTo(MAX_WEIGHT) > 0) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    private static String requireText(
        String value,
        String message
    ) {
        Objects.requireNonNull(value, message);

        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }
}
