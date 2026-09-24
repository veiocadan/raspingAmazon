package com.raspingamazon.application.operation.evaluation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Projeção operacional de um fator persistido do score.
 *
 * <p>Os valores apresentados são históricos e não são
 * normalizados novamente pela consulta operacional.</p>
 */
public record OperationalScoreFactorResult(
    int factorOrder,
    String factorCode,
    String status,
    BigDecimal rawValue,
    BigDecimal normalizedValue,
    BigDecimal weight,
    BigDecimal contribution
) {

    public OperationalScoreFactorResult {

        if (factorOrder < 0) {
            throw new IllegalArgumentException(
                "OperationalScoreFactorResult factorOrder "
                    + "must not be negative"
            );
        }

        factorCode =
            requireText(
                factorCode,
                "OperationalScoreFactorResult "
                    + "factorCode must not be blank"
            );

        status =
            requireText(
                status,
                "OperationalScoreFactorResult "
                    + "status must not be blank"
            );

        Objects.requireNonNull(
            weight,
            "OperationalScoreFactorResult weight must not be null"
        );

        Objects.requireNonNull(
            contribution,
            "OperationalScoreFactorResult contribution must not be null"
        );
    }

    private static String requireText(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
