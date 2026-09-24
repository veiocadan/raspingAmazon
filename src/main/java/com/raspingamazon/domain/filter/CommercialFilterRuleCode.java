package com.raspingamazon.domain.filter;

/**
 * Vocabulário estável das regras comerciais aplicadas pelo motor
 * de filtros configuráveis.
 *
 * <p>Cada valor representa uma regra individual que pode gerar um
 * EvaluationRuleResult persistido e auditável.</p>
 *
 * <p>Códigos históricos não devem ser removidos ou reutilizados com
 * nova semântica.</p>
 */
public enum CommercialFilterRuleCode {

    /**
     * Regra histórica do COMMERCIAL_FILTER_V1.
     *
     * <p>Verifica o desconto explicitamente informado em condições
     * CASH reconhecidas.</p>
     *
     * <p>Permanece no vocabulário para interpretação de avaliações
     * persistidas anteriormente.</p>
     */
    MIN_CASH_DISCOUNT,

    /**
     * Regra introduzida pela ADR-0005.
     *
     * <p>Verifica o desconto derivado entre basisPrice e o preço
     * efetivo da oferta.</p>
     */
    MIN_BASIS_DISCOUNT,

    /**
     * Verifica se a avaliação agregada do produto atende ao mínimo
     * definido pelo perfil comercial.
     */
    MIN_RATING,

    /**
     * Verifica se a quantidade agregada de avaliações atende ao
     * mínimo definido pelo perfil comercial.
     */
    MIN_REVIEW_COUNT
}
