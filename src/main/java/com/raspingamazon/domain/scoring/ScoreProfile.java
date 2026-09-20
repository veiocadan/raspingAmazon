package com.raspingamazon.domain.scoring;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Configuração versionada utilizada pelo motor de score.
 *
 * <p>ScoreProfile define os pesos dos fatores e os parâmetros
 * necessários para normalização. O motor de score não deve
 * possuir pesos ou limiares comerciais hardcoded.</p>
 *
 * <p>A versão faz parte do contrato histórico. Uma alteração
 * semântica em pesos, normalização ou parâmetros deve produzir
 * uma nova versão de perfil.</p>
 */
public record ScoreProfile(
    String version,
    BigDecimal soldPercentageWeight,
    BigDecimal cashDiscountWeight,
    BigDecimal ratingWeight,
    BigDecimal reviewCountWeight,
    long reviewCountFullScoreThreshold
) {

    private static final BigDecimal MIN_WEIGHT = BigDecimal.ZERO;
    private static final BigDecimal MAX_WEIGHT = new BigDecimal("100");
    private static final BigDecimal MAX_TOTAL_WEIGHT = new BigDecimal("100");

    public ScoreProfile {

        version = requireText(
            version,
            "ScoreProfile version must not be blank"
        );

        soldPercentageWeight = requireWeight(
            soldPercentageWeight,
            "ScoreProfile soldPercentageWeight must be between 0 and 100"
        );

        cashDiscountWeight = requireWeight(
            cashDiscountWeight,
            "ScoreProfile cashDiscountWeight must be between 0 and 100"
        );

        ratingWeight = requireWeight(
            ratingWeight,
            "ScoreProfile ratingWeight must be between 0 and 100"
        );

        reviewCountWeight = requireWeight(
            reviewCountWeight,
            "ScoreProfile reviewCountWeight must be between 0 and 100"
        );

        if (reviewCountFullScoreThreshold <= 0) {
            throw new IllegalArgumentException(
                "ScoreProfile reviewCountFullScoreThreshold must be positive"
            );
        }

        BigDecimal totalWeight = soldPercentageWeight
            .add(cashDiscountWeight)
            .add(ratingWeight)
            .add(reviewCountWeight);

        if (totalWeight.compareTo(MAX_TOTAL_WEIGHT) > 0) {
            throw new IllegalArgumentException(
                "ScoreProfile total active weight must not exceed 100"
            );
        }
    }

    /**
     * Retorna o peso configurado para um fator.
     */
    public BigDecimal weightFor(
        ScoreFactorCode factorCode
    ) {
        Objects.requireNonNull(
            factorCode,
            "ScoreFactorCode must not be null"
        );

        return switch (factorCode) {
            case SOLD_PERCENTAGE -> soldPercentageWeight;
            case CASH_DISCOUNT -> cashDiscountWeight;
            case RATING -> ratingWeight;
            case REVIEW_COUNT -> reviewCountWeight;
        };
    }

    /**
     * Soma dos pesos ativos desta versão do score.
     *
     * <p>No SCORE_V1 essa soma poderá ser inferior a 100 porque
     * a parcela originalmente reservada para atratividade de
     * preço não será redistribuída enquanto não existir uma
     * definição confiável para esse fator.</p>
     */
    public BigDecimal totalActiveWeight() {
        return soldPercentageWeight
            .add(cashDiscountWeight)
            .add(ratingWeight)
            .add(reviewCountWeight);
    }

    private static BigDecimal requireWeight(
        BigDecimal value,
        String message
    ) {
        Objects.requireNonNull(
            value,
            message
        );

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
        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }
}
