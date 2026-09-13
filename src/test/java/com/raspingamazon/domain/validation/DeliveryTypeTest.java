package com.raspingamazon.domain.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Valida os valores controlados de DeliveryType.
 *
 * A existência de UNKNOWN é deliberada: o domínio não deve transformar
 * ausência de informação em uma conclusão positiva sobre a entrega.
 */
class DeliveryTypeTest {

    @Test
    void shouldContainAmazonDeliveryType() {
        assertNotNull(DeliveryType.AMAZON);
    }

    @Test
    void shouldContainThirdPartyDeliveryType() {
        assertNotNull(DeliveryType.THIRD_PARTY);
    }

    @Test
    void shouldContainUnknownDeliveryType() {
        assertNotNull(DeliveryType.UNKNOWN);
    }

    @Test
    void shouldContainExactlyThreeDeliveryTypes() {
        assertEquals(3, DeliveryType.values().length);
    }
}