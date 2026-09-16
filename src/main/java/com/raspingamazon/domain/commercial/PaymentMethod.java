package com.raspingamazon.domain.commercial;

/**
 * Representa um método de pagamento identificado para uma condição
 * comercial.
 *
 * Os valores são definidos no domínio para que as regras comerciais
 * não dependam da representação utilizada pelo banco de dados.
 */
public enum PaymentMethod {

    /**
     * Pagamento via Pix.
     */
    PIX,

    /**
     * NuPay utilizando Limite Adicional.
     */
    NUPAY_ADDITIONAL_LIMIT,

    /**
     * Pagamento utilizando cartão de crédito.
     */
    CREDIT_CARD
}