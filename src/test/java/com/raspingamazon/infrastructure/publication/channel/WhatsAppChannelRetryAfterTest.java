package com.raspingamazon.infrastructure.publication.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.channel.PublicationResultStatus;
import com.raspingamazon.infrastructure.config.WhatsAppChannelConfig;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppChannelRetryAfterTest {

    private static final Instant NOW =
        Instant.parse(
            "2026-10-06T21:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

    @Test
    void http429ShouldCarryRetryAfterAsProviderFloor() {

        WhatsAppChannel channel =
            channelReturning(
                new PublicationHttpResponse(
                    429,
                    "{\"error\":{\"message\":\"rate limited\"}}",
                    Map.of(
                        "Retry-After",
                        List.of(
                            "180"
                        )
                    )
                )
            );

        PublicationResult result =
            channel.publish(
                command()
            );

        assertEquals(
            PublicationResultStatus.FAILED_TRANSIENT,
            result.status()
        );

        assertEquals(
            "WHATSAPP_RATE_LIMITED",
            result.errorCode()
        );

        assertEquals(
            OffsetDateTime.ofInstant(
                NOW,
                ZoneOffset.UTC
            ).plusSeconds(
                180
            ),
            result.retryNotBeforeValue()
                .orElseThrow()
        );
    }

    @Test
    void http429WithoutRetryAfterShouldRemainTransientWithoutFloor() {

        WhatsAppChannel channel =
            channelReturning(
                new PublicationHttpResponse(
                    429,
                    "{\"error\":{\"message\":\"rate limited\"}}"
                )
            );

        PublicationResult result =
            channel.publish(
                command()
            );

        assertEquals(
            PublicationResultStatus.FAILED_TRANSIENT,
            result.status()
        );

        assertTrue(
            result.retryNotBeforeValue()
                .isEmpty()
        );
    }

    private WhatsAppChannel channelReturning(
        PublicationHttpResponse response
    ) {

        return new WhatsAppChannel(
            new WhatsAppChannelConfig(
                URI.create(
                    "https://graph.facebook.example"
                ),
                "v23.0",
                "123456789",
                "test-access-token",
                "deal_template",
                "pt_BR",
                Duration.ofSeconds(
                    5
                )
            ),
            request -> response,
            new ObjectMapper(),
            CLOCK
        );
    }

    private PublicationCommand command() {

        return new PublicationCommand(
            1L,
            "WHATSAPP",
            "+5511999999999",
            "Oferta de teste"
        );
    }
}
