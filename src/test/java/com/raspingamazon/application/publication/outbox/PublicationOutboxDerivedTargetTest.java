package com.raspingamazon.application.publication.outbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationOutboxDerivedTargetTest {

    @Test
    void shouldCreateValidTarget() {

        PublicationOutboxDerivedTarget target =
            new PublicationOutboxDerivedTarget(
                "WHATSAPP_MANUAL",
                "-1001234567890"
            );

        assertEquals(
            "WHATSAPP_MANUAL",
            target.channel()
        );

        assertEquals(
            "-1001234567890",
            target.destination()
        );
    }

    @Test
    void shouldTrimValues() {

        PublicationOutboxDerivedTarget target =
            new PublicationOutboxDerivedTarget(
                "  WHATSAPP_MANUAL  ",
                "  -1001234567890  "
            );

        assertEquals(
            "WHATSAPP_MANUAL",
            target.channel()
        );

        assertEquals(
            "-1001234567890",
            target.destination()
        );
    }

    @Test
    void shouldRejectNullChannel() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationOutboxDerivedTarget(
                    null,
                    "-1001234567890"
                )
        );
    }

    @Test
    void shouldRejectBlankChannel() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxDerivedTarget(
                    "   ",
                    "-1001234567890"
                )
        );
    }

    @Test
    void shouldRejectNullDestination() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationOutboxDerivedTarget(
                    "WHATSAPP_MANUAL",
                    null
                )
        );
    }

    @Test
    void shouldRejectBlankDestination() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxDerivedTarget(
                    "WHATSAPP_MANUAL",
                    "\t"
                )
        );
    }
}
