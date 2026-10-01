package com.raspingamazon.infrastructure.publication.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.infrastructure.config.TelegramChannelConfig;
import com.raspingamazon.infrastructure.config.WhatsAppManualStagingConfig;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpRequest;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpResponse;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpTransport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppManualStagingChannelTest {

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    @Test
    void shouldConvertCanonicalContentToWhatsAppMarkupAndSendToStaging()
        throws Exception {

        AtomicReference<PublicationHttpRequest> capturedRequest =
            new AtomicReference<>();

        PublicationHttpTransport transport =
            request -> {

                capturedRequest.set(
                    request
                );

                return new PublicationHttpResponse(
                    200,
                    """
                    {
                      "ok": true,
                      "result": {
                        "message_id": 9001
                      }
                    }
                    """
                );
            };

        WhatsAppManualStagingChannel channel =
            new WhatsAppManualStagingChannel(
                new WhatsAppManualStagingConfig(
                    "-1001234567890"
                ),
                telegramConfig(),
                transport,
                objectMapper
            );

        String canonicalContent =
            """
            🔹**Tênis Reserva Troy**
            💰 De ~~R$ 323,00~~ por **R$ 235,99** à vista no NuPay!
            💸 No Pix: **R$ 239,90** à vista.
            💳 Ou 11x R$ 21,49 sem juros no cartão.
            👇 Tá em Promo!
            🔗 https://www.amazon.com.br/dp/B0TESTE
            """
                .strip();

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    100L,
                    "WHATSAPP_MANUAL",
                    "-1001234567890",
                    canonicalContent
                )
            );

        assertTrue(
            result.successful()
        );

        assertEquals(
            "9001",
            result.providerReferenceValue()
                .orElseThrow()
        );

        PublicationHttpRequest request =
            capturedRequest.get();

        JsonNode body =
            objectMapper.readTree(
                request.body()
            );

        assertEquals(
            "-1001234567890",
            body.path(
                    "chat_id"
                )
                .asText()
        );

        assertEquals(
            """
            🔹*Tênis Reserva Troy*
            💰 De ~R$ 323,00~ por *R$ 235,99* à vista no NuPay!
            💸 No Pix: *R$ 239,90* à vista.
            💳 Ou 11x R$ 21,49 sem juros no cartão.
            👇 Tá em Promo!
            🔗 https://www.amazon.com.br/dp/B0TESTE
            """
                .strip(),
            body.path(
                    "text"
                )
                .asText()
        );

        /*
         * O staging não deve produzir preview visual.
         */
        JsonNode previewOptions =
            body.path(
                "link_preview_options"
            );

        assertTrue(
            previewOptions.path(
                    "is_disabled"
                )
                .asBoolean()
        );

        assertFalse(
            previewOptions.has(
                "prefer_large_media"
            )
        );
    }

    @Test
    void shouldRejectDestinationDifferentFromConfiguredStaging() {

        AtomicInteger transportCalls =
            new AtomicInteger();

        PublicationHttpTransport transport =
            request -> {

                transportCalls.incrementAndGet();

                throw new IllegalStateException(
                    "HTTP must not be called"
                );
            };

        WhatsAppManualStagingChannel channel =
            new WhatsAppManualStagingChannel(
                new WhatsAppManualStagingConfig(
                    "-1001234567890"
                ),
                telegramConfig(),
                transport,
                objectMapper
            );

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    101L,
                    "WHATSAPP_MANUAL",
                    "-1009999999999",
                    "Oferta"
                )
            );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "WHATSAPP_MANUAL_INVALID_DESTINATION",
            result.errorCodeValue()
                .orElseThrow()
        );

        assertEquals(
            0,
            transportCalls.get()
        );
    }

    @Test
    void shouldPreserveTelegramProviderReference() {

        PublicationHttpTransport transport =
            request ->
                new PublicationHttpResponse(
                    200,
                    """
                    {
                      "ok": true,
                      "result": {
                        "message_id": 4321
                      }
                    }
                    """
                );

        WhatsAppManualStagingChannel channel =
            new WhatsAppManualStagingChannel(
                new WhatsAppManualStagingConfig(
                    "-1001234567890"
                ),
                telegramConfig(),
                transport,
                objectMapper
            );

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    102L,
                    "WHATSAPP_MANUAL",
                    "-1001234567890",
                    """
                    🔹**Produto**
                    💰 Por **R$ 100,00**!
                    👇 Tá em Promo!
                    🔗 https://example.com
                    """
                        .strip()
                )
            );

        assertTrue(
            result.successful()
        );

        assertEquals(
            "4321",
            result.providerReferenceValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldAllowFormatterInjection() {

        AtomicReference<PublicationHttpRequest> capturedRequest =
            new AtomicReference<>();

        PublicationHttpTransport transport =
            request -> {

                capturedRequest.set(
                    request
                );

                return new PublicationHttpResponse(
                    200,
                    """
                    {
                      "ok": true,
                      "result": {
                        "message_id": 5000
                      }
                    }
                    """
                );
            };

        WhatsAppManualStagingChannel channel =
            new WhatsAppManualStagingChannel(
                new WhatsAppManualStagingConfig(
                    "-1001234567890"
                ),
                telegramConfig(),
                transport,
                objectMapper,
                content ->
                    "FORMATTED: " + content
            );

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    103L,
                    "WHATSAPP_MANUAL",
                    "-1001234567890",
                    "conteúdo canônico"
                )
            );

        assertTrue(
            result.successful()
        );

        JsonNode body;

        try {

            body =
                objectMapper.readTree(
                    capturedRequest
                        .get()
                        .body()
                );

        } catch (Exception exception) {

            throw new AssertionError(
                exception
            );
        }

        assertEquals(
            "FORMATTED: conteúdo canônico",
            body.path(
                    "text"
                )
                .asText()
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
            ),
            true,
            TelegramChannelConfig.LinkPreviewPosition.ABOVE,
            TelegramChannelConfig.LinkPreviewSize.LARGE
        );
    }
}
