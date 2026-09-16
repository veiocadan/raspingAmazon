package com.raspingamazon.application.collection.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Valida o contrato básico da exceção de coleta.
 */
class CollectionExceptionTest {

    @Test
    void shouldCreateExceptionWithMessage() {
        var exception = new CollectionException("Collection failed");

        assertEquals(
                "Collection failed",
                exception.getMessage()
        );
    }

    @Test
    void shouldPreserveOriginalCause() {
        var cause = new IllegalStateException("Connection failed");

        var exception = new CollectionException(
                "Collection failed",
                cause
        );

        assertEquals(
                "Collection failed",
                exception.getMessage()
        );

        assertSame(
                cause,
                exception.getCause()
        );
    }
}