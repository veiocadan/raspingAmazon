package com.raspingamazon.domain.commercial;

/**
 * Representa um método de pagamento identificado para uma condição
 * comercial.
 *
 * <p>Os valores são definidos no domínio para que as regras comerciais
 * não dependam da representação utilizada pelo banco de dados.</p>
 *
 * <p>NUPAY e NUPAY_ADDITIONAL_LIMIT são conceitos distintos:</p>
 *
 * <ul>
 *     <li>NUPAY representa NuPay explicitamente informado sem
 *         qualificação adicional;</li>
 *     <li>NUPAY_ADDITIONAL_LIMIT representa somente a condição em que
 *         "Limite Adicional" também foi explicitamente observado.</li>
 * </ul>
 */
public enum PaymentMethod {

    /**
     * Pagamento via Pix.
     */
    PIX,

    /**
     * Pagamento via NuPay sem qualificação adicional observada.
     */
    NUPAY,

    /**
     * NuPay utilizando Limite Adicional explicitamente observado.
     */
    NUPAY_ADDITIONAL_LIMIT,

    /**
     * Pagamento utilizando cartão de crédito.
     */
    CREDIT_CARD
}
