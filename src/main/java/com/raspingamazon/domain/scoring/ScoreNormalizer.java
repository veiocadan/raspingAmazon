package com.raspingamazon.domain.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Funções matemáticas determinísticas utilizadas pelo motor de score.
 *
 * <p>Esta classe não conhece banco de dados, elegibilidade,
 * filtros comerciais ou persistência. Sua única responsabilidade
 * é transformar valores brutos em valores normalizados e calcular
 * contribuições ponderadas.</p>
 */
public final class ScoreNormalizer {

    public static final int NORMALIZED_SCALE = 4;
    public static final int CONTRIBUTION_SCALE = 4;

    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal FIVE = new BigDecimal("5");

    private ScoreNormalizer() {
        throw new AssertionError(
            "ScoreNormalizer must not be instantiated"
        );
    }

    /**
     * Normaliza um percentual que já está naturalmente na escala 0..100.
     */
    public static BigDecimal normalizePercentage(
        BigDecimal percentage
    ) {
        Objects.requireNonNull(
            percentage,
            "percentage must not be null"
        );

        requireBetweenZeroAndOneHundred(
            percentage,
            "percentage must be between 0 and 100"
        );

        return percentage.setScale(
            NORMALIZED_SCALE,
            ROUNDING_MODE
        );
    }

    /**
     * Normaliza uma avaliação da escala 0..5 para a escala 0..100.
     *
     * <pre>
     * 4.5 / 5 * 100 = 90
     * </pre>
     */
    public static BigDecimal normalizeRating(
        BigDecimal rating
    ) {
        Objects.requireNonNull(
            rating,
            "rating must not be null"
        );

        if (rating.compareTo(ZERO) < 0
            || rating.compareTo(FIVE) > 0) {

            throw new IllegalArgumentException(
                "rating must be between 0 and 5"
            );
        }

        return rating
            .multiply(ONE_HUNDRED)
            .divide(
                FIVE,
                NORMALIZED_SCALE,
                ROUNDING_MODE
            );
    }

    /**
     * Normaliza a quantidade de avaliações utilizando um limiar
     * configurado no ScoreProfile.
     *
     * <p>Valores iguais ou superiores ao limiar recebem 100 pontos
     * normalizados. Valores inferiores são proporcionais.</p>
     *
     * <pre>
     * reviewCount = 500
     * threshold   = 1000
     *
     * normalized = 500 / 1000 * 100 = 50
     * </pre>
     */
    public static BigDecimal normalizeReviewCount(
        long reviewCount,
        long fullScoreThreshold
    ) {
        if (reviewCount < 0) {
            throw new IllegalArgumentException(
                "reviewCount must not be negative"
            );
        }

        if (fullScoreThreshold <= 0) {
            throw new IllegalArgumentException(
                "fullScoreThreshold must be positive"
            );
        }

        if (reviewCount >= fullScoreThreshold) {
            return ONE_HUNDRED.setScale(
                NORMALIZED_SCALE,
                ROUNDING_MODE
            );
        }

        return BigDecimal.valueOf(reviewCount)
            .multiply(ONE_HUNDRED)
            .divide(
                BigDecimal.valueOf(fullScoreThreshold),
                NORMALIZED_SCALE,
                ROUNDING_MODE
            );
    }

    /**
     * Calcula a contribuição efetiva de um fator.
     *
     * <pre>
     * normalized = 90
     * weight     = 20
     *
     * contribution = 90 / 100 * 20 = 18
     * </pre>
     */
    public static BigDecimal calculateContribution(
        BigDecimal normalizedValue,
        BigDecimal weight
    ) {
        Objects.requireNonNull(
            normalizedValue,
            "normalizedValue must not be null"
        );

        Objects.requireNonNull(
            weight,
            "weight must not be null"
        );

        requireBetweenZeroAndOneHundred(
            normalizedValue,
            "normalizedValue must be between 0 and 100"
        );

        requireBetweenZeroAndOneHundred(
            weight,
            "weight must be between 0 and 100"
        );

        return normalizedValue
            .multiply(weight)
            .divide(
                ONE_HUNDRED,
                CONTRIBUTION_SCALE,
                ROUNDING_MODE
            );
    }

    private static void requireBetweenZeroAndOneHundred(
        BigDecimal value,
        String message
    ) {
        if (value.compareTo(ZERO) < 0
            || value.compareTo(ONE_HUNDRED) > 0) {

            throw new IllegalArgumentException(message);
        }
    }
}
