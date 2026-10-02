package com.raspingamazon.infrastructure.publication.channel;

import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisabledPublicationChannelTest {

    @Test
    void shouldReturnPermanentFailureWithConfiguredErrorCode() {

        DisabledPublicationChannel channel =
            new DisabledPublicationChannel(
                "WHATSAPP_DISABLED"
            );

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    10L,
                    "WHATSAPP",
                    "5511999999999",
                    "Oferta"
                )
            );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "WHATSAPP_DISABLED",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldRejectNullCommand() {

        DisabledPublicationChannel channel =
            new DisabledPublicationChannel(
                "CHANNEL_DISABLED"
            );

        assertThrows(
            NullPointerException.class,
            () ->
                channel.publish(
                    null
                )
        );
    }
}
