package com.raspingamazon.domain.evaluation;

/**
 * Vocabulário controlado dos motivos pelos quais uma oferta pode
 * ser rejeitada.
 *
 * <p>Os códigos podem ser persistidos e, portanto, valores históricos
 * não devem ser removidos ou receber nova semântica.</p>
 */
public enum RejectionReason {

    /**
     * Não existe evidência suficiente sobre o vendedor.
     */
    SELLER_UNKNOWN,

    /**
     * A oferta é vendida por terceiro.
     */
    SELLER_THIRD_PARTY,

    /**
     * Não foi possível determinar quem realiza a entrega.
     */
    DELIVERY_UNKNOWN,

    /**
     * A entrega é realizada por terceiro.
     */
    DELIVERY_THIRD_PARTY,

    /**
     * Não existem dados mínimos suficientes e não existe um motivo
     * mais específico.
     */
    INSUFFICIENT_DATA,

    /**
     * Motivo histórico do COMMERCIAL_FILTER_V1.
     *
     * Não existe desconto CASH explícito reconhecido.
     */
    CASH_DISCOUNT_UNAVAILABLE,

    /**
     * Motivo histórico do COMMERCIAL_FILTER_V1.
     *
     * O desconto CASH explícito existe, mas está abaixo do mínimo.
     */
    CASH_DISCOUNT_BELOW_MINIMUM,

    /**
     * Não existem dados consistentes suficientes para calcular o
     * desconto entre basisPrice e effectivePrice.
     */
    BASIS_DISCOUNT_UNAVAILABLE,

    /**
     * O desconto derivado entre basisPrice e effectivePrice existe,
     * mas está abaixo do mínimo configurado.
     */
    BASIS_DISCOUNT_BELOW_MINIMUM,

    /**
     * O rating agregado não está disponível.
     */
    RATING_UNAVAILABLE,

    /**
     * O rating está abaixo do mínimo.
     */
    RATING_BELOW_MINIMUM,

    /**
     * A quantidade de avaliações não está disponível.
     */
    REVIEW_COUNT_UNAVAILABLE,

    /**
     * A quantidade de avaliações está abaixo do mínimo.
     */
    REVIEW_COUNT_BELOW_MINIMUM
}
