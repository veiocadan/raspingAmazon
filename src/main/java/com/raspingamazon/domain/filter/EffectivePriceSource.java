package com.raspingamazon.domain.filter;

/**
 * Identifica a origem do preço efetivo utilizado para calcular
 * o desconto em relação ao preço-base.
 *
 * <p>O código faz parte da explicabilidade do cálculo derivado.</p>
 *
 * <p>CASH_CONDITION significa que existia uma condição CASH com
 * preço numérico explicitamente observado.</p>
 *
 * <p>CURRENT_PRICE significa que nenhuma condição CASH possuía
 * preço numérico e, portanto, o preço corrente da oferta foi usado
 * para comparação com basisPrice.</p>
 *
 * <p>CURRENT_PRICE não significa Pix, NuPay ou qualquer outra
 * modalidade específica de pagamento.</p>
 */
public enum EffectivePriceSource {

    /**
     * Preço proveniente de uma PaymentCondition do tipo CASH.
     */
    CASH_CONDITION,

    /**
     * Fallback para o currentPrice da própria oferta.
     */
    CURRENT_PRICE
}
