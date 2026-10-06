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
import com.raspingamazon.infrastructure.publication.http.PublicationHttpTransportException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

                return telegramSuccess(
                    9001
                );
            };

        WhatsAppManualStagingChannel channel =
            channel(
                transport
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

        JsonNode body =
            objectMapper.readTree(
                capturedRequest.get()
                    .body()
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

        PublicationResult result =
            channel(
                transport
            ).publish(
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

        PublicationResult result =
            channel(
                request ->
                    telegramSuccess(
                        4321
                    )
            ).publish(
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
    void shouldPropagateTelegramTransportAmbiguityAsDeliveryUnknown() {

        PublicationHttpTransport transport =
            request -> {

                throw new PublicationHttpTransportException(
                    "test transport failure",
                    new IOException(
                        "connection unavailable"
                    )
                );
            };

        PublicationResult result =
            channel(
                transport
            ).publish(
                new PublicationCommand(
                    104L,
                    "WHATSAPP_MANUAL",
                    "-1001234567890",
                    "Oferta"
                )
            );

        assertTrue(
            result.deliveryUnknown()
        );

        assertEquals(
            "TELEGRAM_TRANSPORT_ERROR",
            result.errorCodeValue()
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

                return telegramSuccess(
                    5000
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

    private WhatsAppManualStagingChannel channel(
        PublicationHttpTransport transport
    ) {

        return new WhatsAppManualStagingChannel(
            new WhatsAppManualStagingConfig(
                "-1001234567890"
            ),
            telegramConfig(),
            transport,
            objectMapper
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

    private PublicationHttpResponse telegramSuccess(
        int messageId
    ) {

        return new PublicationHttpResponse(
            200,
            """
            {
              "ok": true,
              "result": {
                "message_id": %d
              }
            }
            """.formatted(
                messageId
            )
        );
    }
}
