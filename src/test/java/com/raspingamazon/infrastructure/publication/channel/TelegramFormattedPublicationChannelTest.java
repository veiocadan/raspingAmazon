package com.raspingamazon.infrastructure.publication.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.infrastructure.config.TelegramChannelConfig;
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

class TelegramFormattedPublicationChannelTest {

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    @Test
    void shouldSendCanonicalPublicationAsTelegramHtml()
        throws Exception {

        AtomicReference<PublicationHttpRequest> capturedRequest =
            new AtomicReference<>();

        PublicationHttpTransport transport =
            request -> {

                capturedRequest.set(
                    request
                );

                return successfulResponse(
                    9001
                );
            };

        TelegramChannel channel =
            new TelegramChannel(
                telegramConfig(),
                transport,
                objectMapper,
                TelegramChannel.MessageFormat.HTML
            );

        String canonical =
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
                    "TELEGRAM",
                    "-1001234567890",
                    canonical
                )
            );

        assertTrue(
            result.successful()
        );

        JsonNode body =
            objectMapper.readTree(
                capturedRequest
                    .get()
                    .body()
            );

        assertEquals(
            """
            🔹<b>Tênis Reserva Troy</b>
            💰 De <s>R$ 323,00</s> por <b>R$ 235,99</b> à vista no NuPay!
            💸 No Pix: <b>R$ 239,90</b> à vista.
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

        assertEquals(
            "HTML",
            body.path(
                    "parse_mode"
                )
                .asText()
        );

        JsonNode preview =
            body.path(
                "link_preview_options"
            );

        assertEquals(
            "https://www.amazon.com.br/dp/B0TESTE",
            preview.path(
                    "url"
                )
                .asText()
        );

        assertTrue(
            preview.path(
                    "show_above_text"
                )
                .asBoolean()
        );

        assertTrue(
            preview.path(
                    "prefer_large_media"
                )
                .asBoolean()
        );
    }

    @Test
    void shouldEscapeHtmlWithoutChangingExplicitPreviewUrl()
        throws Exception {

        AtomicReference<PublicationHttpRequest> capturedRequest =
            new AtomicReference<>();

        PublicationHttpTransport transport =
            request -> {

                capturedRequest.set(
                    request
                );

                return successfulResponse(
                    9002
                );
            };

        TelegramChannel channel =
            new TelegramChannel(
                telegramConfig(),
                transport,
                objectMapper,
                TelegramChannel.MessageFormat.HTML
            );

        String url =
            "https://example.com/oferta?a=1&b=2";

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    101L,
                    "TELEGRAM",
                    "-1001234567890",
                    "🔹**TV A&B <2026>**\n🔗 " + url
                )
            );

        assertTrue(
            result.successful()
        );

        JsonNode body =
            objectMapper.readTree(
                capturedRequest
                    .get()
                    .body()
            );

        assertEquals(
            "🔹<b>TV A&amp;B &lt;2026&gt;</b>\n"
                + "🔗 https://example.com/oferta?a=1&amp;b=2",
            body.path(
                    "text"
                )
                .asText()
        );

        assertEquals(
            url,
            body.path(
                    "link_preview_options"
                )
                .path(
                    "url"
                )
                .asText()
        );
    }

    @Test
    void shouldKeepPlainModeWithoutParseMode()
        throws Exception {

        AtomicReference<PublicationHttpRequest> capturedRequest =
            new AtomicReference<>();

        PublicationHttpTransport transport =
            request -> {

                capturedRequest.set(
                    request
                );

                return successfulResponse(
                    9003
                );
            };

        TelegramChannel channel =
            new TelegramChannel(
                telegramConfig(),
                transport,
                objectMapper,
                TelegramChannel.MessageFormat.PLAIN
            );

        String content =
            "🔹*Produto*\n"
                + "💰 De ~R$ 100,00~ por *R$ 80,00*!";

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    102L,
                    "WHATSAPP_MANUAL",
                    "-1001234567890",
                    content
                )
            );

        assertTrue(
            result.successful()
        );

        JsonNode body =
            objectMapper.readTree(
                capturedRequest
                    .get()
                    .body()
            );

        assertEquals(
            content,
            body.path(
                    "text"
                )
                .asText()
        );

        assertFalse(
            body.has(
                "parse_mode"
            )
        );
    }

    @Test
    void shouldRejectInvalidCanonicalMarkupBeforeHttpCall() {

        AtomicInteger transportCalls =
            new AtomicInteger();

        PublicationHttpTransport transport =
            request -> {

                transportCalls.incrementAndGet();

                return successfulResponse(
                    9004
                );
            };

        TelegramChannel channel =
            new TelegramChannel(
                telegramConfig(),
                transport,
                objectMapper,
                TelegramChannel.MessageFormat.HTML
            );

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    103L,
                    "TELEGRAM",
                    "-1001234567890",
                    "🔹**Título sem fechamento"
                )
            );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "TELEGRAM_CONTENT_FORMATTING_ERROR",
            result.errorCodeValue()
                .orElseThrow()
        );

        assertEquals(
            0,
            transportCalls.get()
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

    private PublicationHttpResponse successfulResponse(
        long messageId
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
            """
                .formatted(
                    messageId
                )
        );
    }
}
