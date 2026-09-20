package com.raspingamazon.domain.scoring;

/**
 * Identifica de forma estável os fatores que podem participar
 * do cálculo de score de uma oferta.
 *
 * <p>Os códigos fazem parte do contrato histórico do score.
 * Eles poderão ser persistidos posteriormente para permitir
 * reconstrução e auditoria das avaliações.</p>
 *
 * <p>Adicionar um novo fator não significa automaticamente
 * alterar uma versão de score existente. A composição efetiva
 * pertence ao ScoreProfile versionado.</p>
 */
public enum ScoreFactorCode {

    /**
     * Percentual explicitamente vendido/consumido da promoção.
     *
     * <p>Na FASE 9 esse dado não é filtro eliminatório.
     * Na FASE 10 ele pode atuar como sinal de popularidade,
     * tração ou demanda observada.</p>
     */
    SOLD_PERCENTAGE,

    /**
     * Melhor desconto à vista explicitamente observado em uma
     * condição comercial reconhecida pelo sistema.
     */
    CASH_DISCOUNT,

    /**
     * Nota agregada do produto.
     */
    RATING,

    /**
     * Quantidade agregada de avaliações do produto.
     */
    REVIEW_COUNT
}
