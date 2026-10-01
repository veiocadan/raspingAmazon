package com.raspingamazon.application.publication.outbox;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationOutboxDerivedEnqueueRequestTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-30T20:00:00Z"
        );

    @Test
    void shouldCreateValidDerivedEnqueueRequest() {

        PublicationOutboxDerivedEnqueueRequest request =
            new PublicationOutboxDerivedEnqueueRequest(
                100L,
                "WHATSAPP_MANUAL",
                "-1001234567890",
                NOW
            );

        assertEquals(
            100L,
            request.sourceOutboxId()
        );

        assertEquals(
            "WHATSAPP_MANUAL",
            request.channel()
        );

        assertEquals(
            "-1001234567890",
            request.destination()
        );

        assertEquals(
            NOW,
            request.enqueuedAt()
        );
    }

    @Test
    void shouldRejectNonPositiveSourceOutboxId() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxDerivedEnqueueRequest(
                    0L,
                    "WHATSAPP_MANUAL",
                    "-1001234567890",
                    NOW
                )
        );
    }

    @Test
    void shouldRejectInvalidChannel() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationOutboxDerivedEnqueueRequest(
                    100L,
                    null,
                    "-1001234567890",
                    NOW
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxDerivedEnqueueRequest(
                    100L,
                    "   ",
                    "-1001234567890",
                    NOW
                )
        );
    }

    @Test
    void shouldRejectInvalidDestination() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationOutboxDerivedEnqueueRequest(
                    100L,
                    "WHATSAPP_MANUAL",
                    null,
                    NOW
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxDerivedEnqueueRequest(
                    100L,
                    "WHATSAPP_MANUAL",
                    "\t",
                    NOW
                )
        );
    }

    @Test
    void shouldRejectNullEnqueuedAt() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationOutboxDerivedEnqueueRequest(
                    100L,
                    "WHATSAPP_MANUAL",
                    "-1001234567890",
                    null
                )
        );
    }
}
