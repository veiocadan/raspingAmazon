package com.raspingamazon.application.collection.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceChangedExceptionTest {

    @Test
    void shouldPreserveOperationalErrorCodeAndMessage() {

        SourceChangedException exception =
            new SourceChangedException(
                "AMAZON_PRODUCT_PAGE_STRUCTURE_UNRECOGNIZED",
                "Product structure changed"
            );

        assertEquals(
            "AMAZON_PRODUCT_PAGE_STRUCTURE_UNRECOGNIZED",
            exception.errorCode()
        );

        assertEquals(
            "Product structure changed",
            exception.getMessage()
        );

        assertNull(
            exception.httpStatusCode()
        );
    }

    @Test
    void shouldRejectBlankErrorCode() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SourceChangedException(
                    "   ",
                    "message"
                )
        );
    }

    @Test
    void shouldRejectBlankMessage() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SourceChangedException(
                    "SOURCE_CHANGED",
                    "   "
                )
        );
    }
}
