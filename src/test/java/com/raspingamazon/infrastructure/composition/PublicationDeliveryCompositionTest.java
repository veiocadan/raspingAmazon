package com.raspingamazon.infrastructure.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.publication.channel.PublicationChannelResolver;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.infrastructure.config.PublicationChannelActivationConfig;
import com.raspingamazon.infrastructure.config.TelegramChannelConfig;
import com.raspingamazon.infrastructure.config.WhatsAppChannelConfig;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpResponse;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpTransport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationDeliveryCompositionTest {

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    private final TelegramChannelConfig telegramConfig =
        new TelegramChannelConfig(
            URI.create(
                "https://api.telegram.org"
            ),
            "123456:test-token",
            Duration.ofSeconds(
                10
            )
        );

    private final WhatsAppChannelConfig whatsAppConfig =
        new WhatsAppChannelConfig(
            URI.create(
                "https://graph.facebook.com"
            ),
            "v26.0",
            "123456789012345",
            "secret-access-token",
            "amazon_offer",
            "pt_BR",
            Duration.ofSeconds(
                10
            )
        );

    @Test
    void shouldRouteTelegramAndWhatsAppToConcreteChannels() {

        AtomicInteger telegramCalls =
            new AtomicInteger();

        AtomicInteger whatsAppCalls =
            new AtomicInteger();

        PublicationHttpTransport transport =
            request -> {

                String path =
                    request.uri()
                        .getPath();

                if (path.endsWith(
                    "/sendMessage"
                )) {

                    telegramCalls.incrementAndGet();

                    return new PublicationHttpResponse(
                        200,
                        """
                        {
                          "ok": true,
                          "result": {
                            "message_id": 321
                          }
                        }
                        """
                    );
                }

                if (path.endsWith(
                    "/messages"
                )) {

                    whatsAppCalls.incrementAndGet();

                    return new PublicationHttpResponse(
                        200,
                        """
                        {
                          "messaging_product": "whatsapp",
                          "messages": [
                            {
                              "id": "wamid.composition-test"
                            }
                          ]
                        }
                        """
                    );
                }

                throw new IllegalStateException(
                    "Unexpected provider URI: "
                        + request
                );
            };

        PublicationChannelResolver resolver =
            PublicationDeliveryComposition
                .createChannelResolver(
                    telegramConfig,
                    whatsAppConfig,
                    transport,
                    objectMapper
                );

        PublicationResult telegramResult =
            resolver.resolve(
                    "TELEGRAM"
                )
                .publish(
                    new PublicationCommand(
                        10L,
                        "TELEGRAM",
                        "@offers_channel",
                        "Oferta Telegram"
                    )
                );

        PublicationResult whatsAppResult =
            resolver.resolve(
                    "WHATSAPP"
                )
                .publish(
                    new PublicationCommand(
                        11L,
                        "WHATSAPP",
                        "5511999999999",
                        "Oferta WhatsApp"
                    )
                );

        assertTrue(
            telegramResult.successful()
        );

        assertEquals(
            "321",
            telegramResult
                .providerReferenceValue()
                .orElseThrow()
        );

        assertTrue(
            whatsAppResult.successful()
        );

        assertEquals(
            "wamid.composition-test",
            whatsAppResult
                .providerReferenceValue()
                .orElseThrow()
        );

        assertEquals(
            1,
            telegramCalls.get()
        );

        assertEquals(
            1,
            whatsAppCalls.get()
        );
    }

    @Test
    void shouldKeepChannelIdentityExact() {

        PublicationChannelResolver resolver =
            PublicationDeliveryComposition
                .createChannelResolver(
                    telegramConfig,
                    whatsAppConfig,
                    successfulTransport(),
                    objectMapper
                );

        assertThrows(
            IllegalStateException.class,
            () ->
                resolver.resolve(
                    "telegram"
                )
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                resolver.resolve(
                    "WHATSAPP_UNKNOWN"
                )
        );
    }

    @Test
    void shouldRejectNullConnectionWhenCreatingWorker() {

        assertThrows(
            NullPointerException.class,
            () ->
                PublicationDeliveryComposition.create(
                    null,
                    "publication-worker-test",
                    telegramConfig,
                    whatsAppConfig,
                    successfulTransport(),
                    objectMapper,
                    Clock.systemUTC()
                )
        );
    }

    @Test
    void shouldKeepDisabledWhatsAppRegisteredWithoutConfiguration() {

        AtomicInteger transportCalls =
            new AtomicInteger();

        PublicationHttpTransport transport =
            request -> {

                transportCalls.incrementAndGet();

                throw new IllegalStateException(
                    "HTTP transport must not be called"
                );
            };

        PublicationChannelActivationConfig activationConfig =
            new PublicationChannelActivationConfig(
                true,
                false
            );

        PublicationChannelResolver resolver =
            PublicationDeliveryComposition
                .createChannelResolver(
                    activationConfig,
                    telegramConfig,
                    null,
                    transport,
                    objectMapper
                );

        PublicationResult result =
            resolver.resolve(
                    "WHATSAPP"
                )
                .publish(
                    new PublicationCommand(
                        20L,
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

        assertEquals(
            0,
            transportCalls.get()
        );
    }

    @Test
    void shouldRequireConfigurationWhenChannelIsEnabled() {

        PublicationChannelActivationConfig activationConfig =
            new PublicationChannelActivationConfig(
                false,
                true
            );

        assertThrows(
            NullPointerException.class,
            () ->
                PublicationDeliveryComposition
                    .createChannelResolver(
                        activationConfig,
                        null,
                        null,
                        successfulTransport(),
                        objectMapper
                    )
        );
    }

    private PublicationHttpTransport successfulTransport() {

        return request -> {

            String path =
                request.uri()
                    .getPath();

            if (path.endsWith(
                "/sendMessage"
            )) {

                return new PublicationHttpResponse(
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

            if (path.endsWith(
                "/messages"
            )) {

                return new PublicationHttpResponse(
                    200,
                    """
                    {
                      "messages": [
                        {
                          "id": "wamid.test"
                        }
                      ]
                    }
                    """
                );
            }

            throw new IllegalStateException(
                "Unexpected provider request"
            );
        };
    }
}
