package com.raspingamazon.domain.commercial;

/**
 * Representa o tipo de condição comercial associada a uma oferta.
 *
 * A condição comercial descreve como o preço pode ser obtido ou pago.
 *
 * CASH representa uma condição de pagamento à vista.
 *
 * CREDIT_INSTALLMENT representa uma condição de pagamento parcelado
 * no crédito.
 *
 * O enum pertence ao domínio e não possui conhecimento sobre PostgreSQL,
 * JDBC, HTML, Amazon ou qualquer outra infraestrutura externa.
 */
public enum PaymentConditionType {

    /**
     * Condição de pagamento à vista.
     *
     * Pode estar associada a métodos como Pix e outras modalidades
     * explicitamente identificadas na coleta.
     */
    CASH,

    /**
     * Condição de pagamento parcelado no crédito.
     */
    CREDIT_INSTALLMENT
}