package com.raspingamazon.domain.shared;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testa as invariantes básicas do Value Object Money.
 *
 * Estes testes são exclusivamente de domínio.
 * Não existe dependência de PostgreSQL, JDBC ou qualquer serviço externo.
 */
class MoneyTest {

    @Test
    void shouldCreateMoneyWithValidAmount() {
        Money money = Money.of("199.90");

        assertEquals(
                new BigDecimal("199.90"),
                money.amount()
        );
    }

    @Test
    void shouldCreateZeroMoney() {
        Money money = Money.of("0.00");

        assertEquals(
                new BigDecimal("0.00"),
                money.amount()
        );
    }

    @Test
    void shouldRejectNullAmount() {
        assertThrows(
                NullPointerException.class,
                () -> new Money(null)
        );
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Money.of("-10.00")
        );
    }

    @Test
    void shouldRejectNullStringValue() {
        assertThrows(
                NullPointerException.class,
                () -> Money.of(null)
        );
    }
}