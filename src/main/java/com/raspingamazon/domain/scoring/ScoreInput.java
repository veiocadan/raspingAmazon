package com.raspingamazon.domain.scoring;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Conjunto de fatos necessários para calcular uma versão de score.
 *
 * <p>Exatamente uma semântica de desconto deve estar presente.</p>
 */
public record ScoreInput(
    BigDecimal soldPercentage,
    BigDecimal cashDiscountPercentage,
    BigDecimal basisDiscountPercentage,
    BigDecimal rating,
    long reviewCount
) {

    private static final BigDecimal ZERO =
        BigDecimal.ZERO;

    private static final BigDecimal ONE_HUNDRED =
        new BigDecimal("100");

    private static final BigDecimal FIVE =
        new BigDecimal("5");

    /**
     * Construtor histórico do SCORE_V1.
     */
    public ScoreInput(
        BigDecimal soldPercentage,
        BigDecimal cashDiscountPercentage,
        BigDecimal rating,
        long reviewCount
    ) {
        this(
            soldPercentage,
            requireLegacyCashDiscount(
                cashDiscountPercentage
            ),
            null,
            rating,
            reviewCount
        );
    }

    public ScoreInput {

        if (soldPercentage != null) {
            requireBetween(
                soldPercentage,
                ZERO,
                ONE_HUNDRED,
                "ScoreInput soldPercentage must be between 0 and 100"
            );
        }

        boolean hasCash =
            cashDiscountPercentage != null;

        boolean hasBasis =
            basisDiscountPercentage != null;

        if (hasCash == hasBasis) {
            throw new IllegalArgumentException(
                "ScoreInput must define exactly one discount percentage"
            );
        }

        if (cashDiscountPercentage != null) {
            requireBetween(
                cashDiscountPercentage,
                ZERO,
                ONE_HUNDRED,
                "ScoreInput cashDiscountPercentage must be between 0 and 100"
            );
        }

        if (basisDiscountPercentage != null) {
            requireBetween(
                basisDiscountPercentage,
                ZERO,
                ONE_HUNDRED,
                "ScoreInput basisDiscountPercentage must be between 0 and 100"
            );
        }

        rating =
            Objects.requireNonNull(
                rating,
                "ScoreInput rating must not be null"
            );

        requireBetween(
            rating,
            ZERO,
            FIVE,
            "ScoreInput rating must be between 0 and 5"
        );

        if (reviewCount < 0) {
            throw new IllegalArgumentException(
                "ScoreInput reviewCount must not be negative"
            );
        }
    }

    public static ScoreInput forBasisDiscount(
        BigDecimal soldPercentage,
        BigDecimal basisDiscountPercentage,
        BigDecimal rating,
        long reviewCount
    ) {
        return new ScoreInput(
            soldPercentage,
            null,
            Objects.requireNonNull(
                basisDiscountPercentage,
                "basisDiscountPercentage must not be null"
            ),
            rating,
            reviewCount
        );
    }

    public boolean hasSoldPercentage() {
        return soldPercentage != null;
    }

    public boolean usesCashDiscount() {
        return cashDiscountPercentage != null;
    }

    public boolean usesBasisDiscount() {
        return basisDiscountPercentage != null;
    }

    private static BigDecimal requireLegacyCashDiscount(
        BigDecimal value
    ) {
        return Objects.requireNonNull(
            value,
            "ScoreInput cashDiscountPercentage must not be null"
        );
    }

    private static void requireBetween(
        BigDecimal value,
        BigDecimal minimum,
        BigDecimal maximum,
        String message
    ) {
        if (value.compareTo(minimum) < 0
            || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
