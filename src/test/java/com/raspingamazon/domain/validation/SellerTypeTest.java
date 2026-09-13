package com.raspingamazon.domain.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Valida a existência e os valores controlados de SellerType.
 *
 * Como SellerType é um conceito puro do domínio, estes testes não
 * precisam de banco, Amazon ou qualquer componente de infraestrutura.
 */
class SellerTypeTest {

    @Test
    void shouldContainAmazonSellerType() {
        assertNotNull(SellerType.AMAZON);
    }

    @Test
    void shouldContainThirdPartySellerType() {
        assertNotNull(SellerType.THIRD_PARTY);
    }

    @Test
    void shouldContainUnknownSellerType() {
        assertNotNull(SellerType.UNKNOWN);
    }

    @Test
    void shouldContainExactlyThreeSellerTypes() {
        assertEquals(3, SellerType.values().length);
    }
}