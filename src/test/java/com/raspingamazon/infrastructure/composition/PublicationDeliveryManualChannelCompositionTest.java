package com.raspingamazon.infrastructure.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.publication.channel.PublicationChannelResolver;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.infrastructure.config.PublicationChannelActivationConfig;
import com.raspingamazon.infrastructure.config.TelegramChannelConfig;
import com.raspingamazon.infrastructure.config.WhatsAppManualStagingConfig;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpResponse;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpTransport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationDeliveryManualChannelCompositionTest {

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    @Test
    void shouldRouteWhatsAppManualThroughTelegramEvenWhenPublicTelegramIsDisabled() {

        AtomicInteger transportCalls =
            new AtomicInteger();

        PublicationHttpTransport transport =
            request -> {

                transportCalls.incrementAndGet();

                return new PublicationHttpResponse(
                    200,
                    """
                    {
                      "ok": true,
                      "result": {
                        "message_id": 7001
                      }
                    }
                    """
                );
            };

        PublicationChannelResolver resolver =
            PublicationDeliveryComposition
                .createChannelResolver(
                    new PublicationChannelActivationConfig(
                        false,
                        true,
                        false
                    ),
                    telegramConfig(),
                    new WhatsAppManualStagingConfig(
                        "-1001234567890"
                    ),
                    null,
                    transport,
                    objectMapper
                );

        PublicationResult result =
            resolver.resolve(
                    "WHATSAPP_MANUAL"
                )
                .publish(
                    new PublicationCommand(
                        200L,
                        "WHATSAPP_MANUAL",
                        "-1001234567890",
                        "Mensagem pronta para WhatsApp"
                    )
                );

        assertTrue(
            result.successful()
        );

        assertEquals(
            "7001",
            result.providerReferenceValue()
                .orElseThrow()
        );

        assertEquals(
            1,
            transportCalls.get()
        );
    }

    @Test
    void shouldKeepDisabledWhatsAppManualRegistered() {

        AtomicInteger transportCalls =
            new AtomicInteger();

        PublicationHttpTransport transport =
            request -> {

                transportCalls.incrementAndGet();

                throw new IllegalStateException(
                    "HTTP must not be called"
                );
            };

        PublicationChannelResolver resolver =
            PublicationDeliveryComposition
                .createChannelResolver(
                    new PublicationChannelActivationConfig(
                        false,
                        false,
                        false
                    ),
                    null,
                    null,
                    null,
                    transport,
                    objectMapper
                );

        PublicationResult result =
            resolver.resolve(
                    "WHATSAPP_MANUAL"
                )
                .publish(
                    new PublicationCommand(
                        201L,
                        "WHATSAPP_MANUAL",
                        "-1001234567890",
                        "Mensagem"
                    )
                );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "WHATSAPP_MANUAL_DISABLED",
            result.errorCodeValue()
                .orElseThrow()
        );

        assertEquals(
            0,
            transportCalls.get()
        );
    }

    @Test
    void shouldRequireStagingConfigurationWhenManualWhatsAppIsEnabled() {

        assertThrows(
            NullPointerException.class,
            () ->
                PublicationDeliveryComposition
                    .createChannelResolver(
                        new PublicationChannelActivationConfig(
                            false,
                            true,
                            false
                        ),
                        telegramConfig(),
                        null,
                        null,
                        successfulTransport(),
                        objectMapper
                    )
        );
    }

    private TelegramChannelConfig telegramConfig() {

        return new TelegramChannelConfig(
            URI.create(
                "https://api.telegram.org"
            ),
            "123456:test-token",
            Duration.ofSeconds(
                10
            )
        );
    }

    private PublicationHttpTransport successfulTransport() {

        return request ->
            new PublicationHttpResponse(
                200,
                """
                {
                  "ok": true,
                  "result": {
                    "message_id": 1
                  }
                }
                """
            );
    }
}
