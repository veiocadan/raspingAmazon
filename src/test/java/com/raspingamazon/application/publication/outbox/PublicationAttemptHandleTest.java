package com.raspingamazon.application.publication.outbox;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationAttemptHandleTest {

    private static final OffsetDateTime STARTED_AT =
        OffsetDateTime.parse(
            "2026-10-03T20:30:00Z"
        );

    @Test
    void shouldExposeValidPersistedAttemptIdentity() {

        PublicationAttemptHandle handle =
            new PublicationAttemptHandle(
                10L,
                20L,
                3,
                STARTED_AT
            );

        assertEquals(
            10L,
            handle.id()
        );

        assertEquals(
            20L,
            handle.publicationOutboxId()
        );

        assertEquals(
            3,
            handle.attemptNumber()
        );

        assertEquals(
            STARTED_AT,
            handle.startedAt()
        );
    }

    @Test
    void shouldRejectNonPositiveAttemptId() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationAttemptHandle(
                    0L,
                    20L,
                    1,
                    STARTED_AT
                )
        );
    }

    @Test
    void shouldRejectNonPositiveOutboxId() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationAttemptHandle(
                    10L,
                    0L,
                    1,
                    STARTED_AT
                )
        );
    }

    @Test
    void shouldRejectNonPositiveAttemptNumber() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationAttemptHandle(
                    10L,
                    20L,
                    0,
                    STARTED_AT
                )
        );
    }

    @Test
    void shouldRejectMissingStartedAt() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationAttemptHandle(
                    10L,
                    20L,
                    1,
                    null
                )
        );
    }
}
