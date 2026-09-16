package com.raspingamazon.domain.commercial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testa os tipos de condição comercial disponíveis no domínio.
 */
class PaymentConditionTypeTest {

    @Test
    void shouldContainExpectedConditionTypes() {
        assertEquals(
                2,
                PaymentConditionType.values().length
        );

        assertEquals(
                PaymentConditionType.CASH,
                PaymentConditionType.valueOf("CASH")
        );

        assertEquals(
                PaymentConditionType.CREDIT_INSTALLMENT,
                PaymentConditionType.valueOf("CREDIT_INSTALLMENT")
        );
    }
}