package com.raspingamazon.application.collection.contract;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CollectionResultTest {

    private static final OffsetDateTime COLLECTED_AT =
            OffsetDateTime.parse("2026-09-13T12:00:00-03:00");

    @Test
    void shouldCreateValidResult() {
        var result = new CollectionResult(
                "payload",
                COLLECTED_AT,
                "https://example.com/deals"
        );

        assertEquals("payload", result.content());
        assertEquals(COLLECTED_AT, result.collectedAt());
        assertEquals(
                "https://example.com/deals",
                result.source()
        );
    }

    @Test
    void shouldRejectNullContent() {
        assertThrows(
                NullPointerException.class,
                () -> new CollectionResult(
                        null,
                        COLLECTED_AT,
                        "source"
                )
        );
    }

    @Test
    void shouldRejectBlankContent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CollectionResult(
                        "   ",
                        COLLECTED_AT,
                        "source"
                )
        );
    }

    @Test
    void shouldRejectNullCollectedAt() {
        assertThrows(
                NullPointerException.class,
                () -> new CollectionResult(
                        "payload",
                        null,
                        "source"
                )
        );
    }

    @Test
    void shouldRejectNullSource() {
        assertThrows(
                NullPointerException.class,
                () -> new CollectionResult(
                        "payload",
                        COLLECTED_AT,
                        null
                )
        );
    }

    @Test
    void shouldRejectBlankSource() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CollectionResult(
                        "payload",
                        COLLECTED_AT,
                        " "
                )
        );
    }
}