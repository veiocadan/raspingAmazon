package com.raspingamazon.domain.scoring;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado auditável de um único fator utilizado no cálculo de score.
 *
 * <p>Este objeto não calcula o score. Ele representa o resultado já
 * produzido por um normalizador/motor de score e preserva os dados
 * necessários para explicar posteriormente como a pontuação foi
 * construída.</p>
 *
 * <p>Para um fator disponível:</p>
 *
 * <pre>
 * code            = RATING
 * status          = AVAILABLE
 * rawValue        = 4.5
 * normalizedValue = 90
 * weight          = 20
 * contribution    = 18
 * </pre>
 *
 * <p>Para um fator indisponível:</p>
 *
 * <pre>
 * code            = SOLD_PERCENTAGE
 * status          = UNAVAILABLE
 * rawValue        = null
 * normalizedValue = null
 * weight          = 30
 * contribution    = 0
 * </pre>
 *
 * <p>A distinção entre valor zero e dado indisponível é deliberada.
 * Ausência de evidência não deve ser registrada como se o sistema
 * tivesse observado numericamente o valor zero.</p>
 */
public record ScoreFactorResult(
    ScoreFactorCode code,
    ScoreFactorStatus status,
    BigDecimal rawValue,
    BigDecimal normalizedValue,
    BigDecimal weight,
    BigDecimal contribution
) {

    private static final BigDecimal MIN_PERCENTAGE = BigDecimal.ZERO;
    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");

    public ScoreFactorResult {

        code = Objects.requireNonNull(
            code,
            "ScoreFactorResult code must not be null"
        );

        status = Objects.requireNonNull(
            status,
            "ScoreFactorResult status must not be null"
        );

        weight = Objects.requireNonNull(
            weight,
            "ScoreFactorResult weight must not be null"
        );

        contribution = Objects.requireNonNull(
            contribution,
            "ScoreFactorResult contribution must not be null"
        );

        requireBetweenZeroAndOneHundred(
            weight,
            "ScoreFactorResult weight must be between 0 and 100"
        );

        if (contribution.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                "ScoreFactorResult contribution must not be negative"
            );
        }

        if (contribution.compareTo(weight) > 0) {
            throw new IllegalArgumentException(
                "ScoreFactorResult contribution must not be greater than weight"
            );
        }

        if (status == ScoreFactorStatus.AVAILABLE) {

            rawValue = Objects.requireNonNull(
                rawValue,
                "Available score factor rawValue must not be null"
            );

            normalizedValue = Objects.requireNonNull(
                normalizedValue,
                "Available score factor normalizedValue must not be null"
            );

            if (rawValue.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                    "Available score factor rawValue must not be negative"
                );
            }

            requireBetweenZeroAndOneHundred(
                normalizedValue,
                "Available score factor normalizedValue must be between 0 and 100"
            );
        }

        if (status == ScoreFactorStatus.UNAVAILABLE) {

            if (rawValue != null) {
                throw new IllegalArgumentException(
                    "Unavailable score factor rawValue must be null"
                );
            }

            if (normalizedValue != null) {
                throw new IllegalArgumentException(
                    "Unavailable score factor normalizedValue must be null"
                );
            }

            if (contribution.compareTo(BigDecimal.ZERO) != 0) {
                throw new IllegalArgumentException(
                    "Unavailable score factor contribution must be zero"
                );
            }
        }
    }

    /**
     * Cria o resultado de um fator que possuía evidência disponível.
     */
    public static ScoreFactorResult available(
        ScoreFactorCode code,
        BigDecimal rawValue,
        BigDecimal normalizedValue,
        BigDecimal weight,
        BigDecimal contribution
    ) {
        return new ScoreFactorResult(
            code,
            ScoreFactorStatus.AVAILABLE,
            rawValue,
            normalizedValue,
            weight,
            contribution
        );
    }

    /**
     * Cria o resultado de um fator sem evidência disponível.
     *
     * <p>No SCORE_V1 um fator indisponível contribui com zero para
     * o score, mas continua explicitamente identificado como
     * UNAVAILABLE para fins de auditoria.</p>
     */
    public static ScoreFactorResult unavailable(
        ScoreFactorCode code,
        BigDecimal weight
    ) {
        return new ScoreFactorResult(
            code,
            ScoreFactorStatus.UNAVAILABLE,
            null,
            null,
            weight,
            BigDecimal.ZERO
        );
    }

    /**
     * Informa se o fator possuía valor observável suficiente
     * para participar normalmente do cálculo.
     */
    public boolean isAvailable() {
        return status == ScoreFactorStatus.AVAILABLE;
    }

    private static void requireBetweenZeroAndOneHundred(
        BigDecimal value,
        String message
    ) {
        if (value.compareTo(MIN_PERCENTAGE) < 0
            || value.compareTo(MAX_PERCENTAGE) > 0) {

            throw new IllegalArgumentException(message);
        }
    }
}
