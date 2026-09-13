package com.raspingamazon.domain.evaluation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Testa o vocabulário controlado de motivos de rejeição.
 *
 * O teste verifica deliberadamente a quantidade e a presença dos
 * códigos definidos. Isso ajuda a detectar alterações acidentais
 * no contrato interno do domínio.
 *
 * Nenhuma infraestrutura externa é necessária para este teste.
 */
class RejectionReasonTest {

    @Test
    void shouldContainSellerUnknownReason() {
        assertNotNull(RejectionReason.SELLER_UNKNOWN);
    }

    @Test
    void shouldContainSellerThirdPartyReason() {
        assertNotNull(RejectionReason.SELLER_THIRD_PARTY);
    }

    @Test
    void shouldContainDeliveryUnknownReason() {
        assertNotNull(RejectionReason.DELIVERY_UNKNOWN);
    }

    @Test
    void shouldContainDeliveryThirdPartyReason() {
        assertNotNull(RejectionReason.DELIVERY_THIRD_PARTY);
    }

    @Test
    void shouldContainInsufficientDataReason() {
        assertNotNull(RejectionReason.INSUFFICIENT_DATA);
    }

    @Test
    void shouldContainExactlyFiveRejectionReasons() {
        assertEquals(5, RejectionReason.values().length);
    }
}