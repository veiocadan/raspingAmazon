package com.raspingamazon.application.collection.contract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SourceDataUnavailableExceptionTest {

    @Test
    void shouldPreserveErrorCodeAndMessage() {

        SourceDataUnavailableException exception =
            new SourceDataUnavailableException(
                "AMAZON_PRODUCT_PAGE_NOT_FOUND",
                "Product does not exist"
            );

        assertEquals(
            "AMAZON_PRODUCT_PAGE_NOT_FOUND",
            exception.errorCode()
        );

        assertEquals(
            "Product does not exist",
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
                new SourceDataUnavailableException(
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
                new SourceDataUnavailableException(
                    "DATA_UNAVAILABLE",
                    "   "
                )
        );
    }
}
