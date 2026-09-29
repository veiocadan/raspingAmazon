package com.raspingamazon.application.publication.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationCommandTest {

    @Test
    void shouldCreateSelfContainedPublicationCommand() {

        PublicationCommand command =
            new PublicationCommand(
                10L,
                "TELEGRAM",
                "@phase18",
                "Oferta pronta para publicação"
            );

        assertEquals(
            10L,
            command.publicationId()
        );

        assertEquals(
            "TELEGRAM",
            command.channel()
        );

        assertEquals(
            "@phase18",
            command.destination()
        );

        assertEquals(
            "Oferta pronta para publicação",
            command.content()
        );
    }

    @Test
    void shouldRejectNonPositivePublicationId() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationCommand(
                    0L,
                    "TELEGRAM",
                    "@phase18",
                    "Oferta"
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationCommand(
                    -1L,
                    "TELEGRAM",
                    "@phase18",
                    "Oferta"
                )
        );
    }

    @Test
    void shouldRejectBlankChannel() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationCommand(
                    1L,
                    "   ",
                    "@phase18",
                    "Oferta"
                )
        );
    }

    @Test
    void shouldRejectBlankDestination() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationCommand(
                    1L,
                    "TELEGRAM",
                    " ",
                    "Oferta"
                )
        );
    }

    @Test
    void shouldRejectBlankContent() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationCommand(
                    1L,
                    "TELEGRAM",
                    "@phase18",
                    ""
                )
        );
    }

    @Test
    void shouldRejectNullTextFields() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationCommand(
                    1L,
                    null,
                    "@phase18",
                    "Oferta"
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationCommand(
                    1L,
                    "TELEGRAM",
                    null,
                    "Oferta"
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationCommand(
                    1L,
                    "TELEGRAM",
                    "@phase18",
                    null
                )
        );
    }
}
