package com.raspingamazon.domain.commercial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testa os métodos de pagamento reconhecidos pelo domínio.
 */
class PaymentMethodTest {

    @Test
    void shouldContainExpectedPaymentMethods() {

        assertEquals(
            4,
            PaymentMethod.values()
                .length
        );

        assertEquals(
            PaymentMethod.PIX,
            PaymentMethod.valueOf(
                "PIX"
            )
        );

        assertEquals(
            PaymentMethod.NUPAY,
            PaymentMethod.valueOf(
                "NUPAY"
            )
        );

        assertEquals(
            PaymentMethod.NUPAY_ADDITIONAL_LIMIT,
            PaymentMethod.valueOf(
                "NUPAY_ADDITIONAL_LIMIT"
            )
        );

        assertEquals(
            PaymentMethod.CREDIT_CARD,
            PaymentMethod.valueOf(
                "CREDIT_CARD"
            )
        );
    }
}
