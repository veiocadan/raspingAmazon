package com.raspingamazon.domain.publication.selection;

import com.raspingamazon.domain.product.Asin;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SuccessfulPublicationHistoryTest {

    private static final Asin ASIN =
        new Asin(
            "B0HIST1801"
        );

    private static final Instant LAST_SUCCESS_AT =
        Instant.parse(
            "2026-09-27T15:00:00Z"
        );

    @Test
    void shouldCreateSuccessfulPublicationHistory() {

        SuccessfulPublicationHistory history =
            new SuccessfulPublicationHistory(
                ASIN,
                LAST_SUCCESS_AT,
                3L
            );

        assertEquals(
            ASIN,
            history.asin()
        );

        assertEquals(
            LAST_SUCCESS_AT,
            history.lastSuccessfulPublicationAt()
        );

        assertEquals(
            3L,
            history.successfulPublicationCount()
        );
    }

    @Test
    void shouldRejectNullAsin() {

        assertThrows(
            NullPointerException.class,
            () -> new SuccessfulPublicationHistory(
                null,
                LAST_SUCCESS_AT,
                1L
            )
        );
    }

    @Test
    void shouldRejectNullLastSuccessfulPublicationAt() {

        assertThrows(
            NullPointerException.class,
            () -> new SuccessfulPublicationHistory(
                ASIN,
                null,
                1L
            )
        );
    }

    @Test
    void shouldRejectZeroSuccessfulPublicationCount() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new SuccessfulPublicationHistory(
                ASIN,
                LAST_SUCCESS_AT,
                0L
            )
        );
    }

    @Test
    void shouldRejectNegativeSuccessfulPublicationCount() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new SuccessfulPublicationHistory(
                ASIN,
                LAST_SUCCESS_AT,
                -1L
            )
        );
    }
}
