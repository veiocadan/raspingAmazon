package com.raspingamazon.domain.filter;

/**
 * Vocabulário estável das regras comerciais aplicadas pelo motor
 * de filtros configuráveis.
 *
 * Cada valor representa uma regra individual que poderá gerar um
 * EvaluationRuleResult persistido e auditável.
 *
 * Os códigos não carregam valores de configuração. Os limites
 * pertencem ao FilterProfile.
 *
 * Também não pertencem a esta enumeração as regras estruturais de
 * vendedor e entrega pela Amazon, pois elas continuam sob a política
 * de elegibilidade implementada anteriormente.
 */
public enum CommercialFilterRuleCode {

    /**
     * Verifica se existe uma condição de pagamento à vista reconhecida
     * cujo desconto explicitamente informado atende ao mínimo definido
     * pelo FilterProfile.
     */
    MIN_CASH_DISCOUNT,

    /**
     * Verifica se a avaliação agregada do produto atende ao mínimo
     * definido pelo FilterProfile.
     */
    MIN_RATING,

    /**
     * Verifica se a quantidade agregada de avaliações do produto atende
     * ao mínimo definido pelo FilterProfile.
     */
    MIN_REVIEW_COUNT
}
