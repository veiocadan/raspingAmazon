package com.raspingamazon.domain.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testa as invariantes do Value Object Percentage.
 *
 * Os testes não dependem da Amazon, PostgreSQL ou qualquer outra
 * infraestrutura. O objetivo é validar somente o conceito de domínio.
 */
class PercentageTest {

    @Test
    void shouldCreateValidPercentage() {
        Percentage percentage = Percentage.of("48");

        assertEquals(
                new BigDecimal("48"),
                percentage.value()
        );
    }

    @Test
    void shouldAcceptDecimalPercentage() {
        Percentage percentage = Percentage.of("83.5");

        assertEquals(
                new BigDecimal("83.5"),
                percentage.value()
        );
    }

    @Test
    void shouldAcceptZeroPercentage() {
        Percentage percentage = Percentage.of("0");

        assertEquals(
                new BigDecimal("0"),
                percentage.value()
        );
    }

    @Test
    void shouldAcceptOneHundredPercent() {
        Percentage percentage = Percentage.of("100");

        assertEquals(
                new BigDecimal("100"),
                percentage.value()
        );
    }

    @Test
    void shouldRejectNegativePercentage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Percentage.of("-1")
        );
    }

    @Test
    void shouldRejectPercentageAboveOneHundred() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Percentage.of("100.01")
        );
    }

    @Test
    void shouldRejectNullPercentage() {
        assertThrows(
                NullPointerException.class,
                () -> new Percentage(null)
        );
    }

    @Test
    void shouldRejectNullStringValue() {
        assertThrows(
                NullPointerException.class,
                () -> Percentage.of(null)
        );
    }
}