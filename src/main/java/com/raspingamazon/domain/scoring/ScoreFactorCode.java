package com.raspingamazon.domain.scoring;

/**
 * Identifica de forma estável os fatores que podem participar
 * do cálculo de score.
 *
 * <p>CASH_DISCOUNT preserva a semântica histórica do SCORE_V1.</p>
 *
 * <p>BASIS_DISCOUNT representa o desconto derivado entre basisPrice
 * e effectivePrice definido pela ADR-0006.</p>
 */
public enum ScoreFactorCode {

    SOLD_PERCENTAGE,

    CASH_DISCOUNT,

    BASIS_DISCOUNT,

    RATING,

    REVIEW_COUNT
}
