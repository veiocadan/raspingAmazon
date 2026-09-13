package com.raspingamazon.domain.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsinTest {

    @Test
    void shouldCreateValidAsin() {
        Asin asin = new Asin("B0FN4BK3V7");

        assertEquals("B0FN4BK3V7", asin.value());
    }

    @Test
    void shouldRejectNullAsin() {
        assertThrows(
                NullPointerException.class,
                () -> new Asin(null)
        );
    }

    @Test
    void shouldRejectBlankAsin() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Asin("   ")
        );
    }

    @Test
    void shouldRejectAsinLongerThanTenCharacters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Asin("12345678901")
        );
    }
}