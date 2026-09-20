package com.raspingamazon.domain.scoring;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Conjunto de valores observados necessários para calcular o score.
 *
 * <p>ScoreInput representa fatos já coletados e validados pelas etapas
 * anteriores do pipeline. Ele não contém pesos, versões ou parâmetros
 * de normalização; essas informações pertencem ao ScoreProfile.</p>
 *
 * <p>No SCORE_V1:</p>
 *
 * <ul>
 *     <li>soldPercentage pode estar ausente;</li>
 *     <li>cashDiscountPercentage é obrigatório;</li>
 *     <li>rating é obrigatório;</li>
 *     <li>reviewCount é obrigatório.</li>
 * </ul>
 *
 * <p>A possibilidade de soldPercentage ser null é deliberada:
 * ausência de informação não equivale a percentual vendido igual
 * a zero.</p>
 */
public record ScoreInput(
    BigDecimal soldPercentage,
    BigDecimal cashDiscountPercentage,
    BigDecimal rating,
    long reviewCount
) {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal FIVE = new BigDecimal("5");

    public ScoreInput {

        if (soldPercentage != null) {
            requireBetween(
                soldPercentage,
                ZERO,
                ONE_HUNDRED,
                "ScoreInput soldPercentage must be between 0 and 100"
            );
        }

        cashDiscountPercentage = Objects.requireNonNull(
            cashDiscountPercentage,
            "ScoreInput cashDiscountPercentage must not be null"
        );

        requireBetween(
            cashDiscountPercentage,
            ZERO,
            ONE_HUNDRED,
            "ScoreInput cashDiscountPercentage must be between 0 and 100"
        );

        rating = Objects.requireNonNull(
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

    /**
     * Indica se houve observação explícita do percentual vendido.
     */
    public boolean hasSoldPercentage() {
        return soldPercentage != null;
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
