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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramLinkPreviewTest {

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    @Test
    void shouldRequestLargePreviewAboveTextForFirstMessageUrl()
        throws Exception {

        AtomicReference<PublicationHttpRequest> capturedRequest =
            new AtomicReference<>();

        PublicationHttpTransport transport =
            request -> {

                capturedRequest.set(
                    request
                );

                return successResponse();
            };

        TelegramChannel channel =
            new TelegramChannel(
                new TelegramChannelConfig(
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
                ),
                transport,
                objectMapper
            );

        String link =
            "https://www.amazon.com.br/dp/B0TEST1234?tag=rasping-20";

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    1L,
                    "TELEGRAM",
                    "@rasping_test",
                    """
                    Oferta Amazon

                    Produto de teste

                    https://www.amazon.com.br/dp/B0TEST1234?tag=rasping-20
                    """
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

        JsonNode preview =
            body.path(
                "link_preview_options"
            );

        assertEquals(
            link,
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

        assertFalse(
            preview.has(
                "prefer_small_media"
            )
        );
    }

    @Test
    void shouldSupportSmallPreviewBelowText()
        throws Exception {

        AtomicReference<PublicationHttpRequest> capturedRequest =
            new AtomicReference<>();

        PublicationHttpTransport transport =
            request -> {

                capturedRequest.set(
                    request
                );

                return successResponse();
            };

        TelegramChannel channel =
            new TelegramChannel(
                new TelegramChannelConfig(
                    URI.create(
                        "https://api.telegram.org"
                    ),
                    "123456:test-token",
                    Duration.ofSeconds(
                        10
                    ),
                    true,
                    TelegramChannelConfig.LinkPreviewPosition.BELOW,
                    TelegramChannelConfig.LinkPreviewSize.SMALL
                ),
                transport,
                objectMapper
            );

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    2L,
                    "TELEGRAM",
                    "@rasping_test",
                    "Oferta https://example.com/produto"
                )
            );

        assertTrue(
            result.successful()
        );

        JsonNode preview =
            objectMapper.readTree(
                    capturedRequest
                        .get()
                        .body()
                )
                .path(
                    "link_preview_options"
                );

        assertFalse(
            preview.path(
                    "show_above_text"
                )
                .asBoolean()
        );

        assertTrue(
            preview.path(
                    "prefer_small_media"
                )
                .asBoolean()
        );

        assertFalse(
            preview.has(
                "prefer_large_media"
            )
        );
    }

    @Test
    void shouldExplicitlyDisablePreviewWhenConfigured()
        throws Exception {

        AtomicReference<PublicationHttpRequest> capturedRequest =
            new AtomicReference<>();

        PublicationHttpTransport transport =
            request -> {

                capturedRequest.set(
                    request
                );

                return successResponse();
            };

        TelegramChannel channel =
            new TelegramChannel(
                new TelegramChannelConfig(
                    URI.create(
                        "https://api.telegram.org"
                    ),
                    "123456:test-token",
                    Duration.ofSeconds(
                        10
                    ),
                    false,
                    TelegramChannelConfig.LinkPreviewPosition.ABOVE,
                    TelegramChannelConfig.LinkPreviewSize.LARGE
                ),
                transport,
                objectMapper
            );

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    3L,
                    "TELEGRAM",
                    "@rasping_test",
                    "Oferta https://example.com/produto"
                )
            );

        assertTrue(
            result.successful()
        );

        JsonNode preview =
            objectMapper.readTree(
                    capturedRequest
                        .get()
                        .body()
                )
                .path(
                    "link_preview_options"
                );

        assertTrue(
            preview.path(
                    "is_disabled"
                )
                .asBoolean()
        );

        assertFalse(
            preview.has(
                "prefer_large_media"
            )
        );

        assertFalse(
            preview.has(
                "show_above_text"
            )
        );
    }

    @Test
    void shouldPreservePreviousPayloadWhenMessageHasNoUrl()
        throws Exception {

        AtomicReference<PublicationHttpRequest> capturedRequest =
            new AtomicReference<>();

        PublicationHttpTransport transport =
            request -> {

                capturedRequest.set(
                    request
                );

                return successResponse();
            };

        TelegramChannel channel =
            new TelegramChannel(
                new TelegramChannelConfig(
                    URI.create(
                        "https://api.telegram.org"
                    ),
                    "123456:test-token",
                    Duration.ofSeconds(
                        10
                    )
                ),
                transport,
                objectMapper
            );

        PublicationResult result =
            channel.publish(
                new PublicationCommand(
                    4L,
                    "TELEGRAM",
                    "@rasping_test",
                    "Mensagem sem link"
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

        assertFalse(
            body.has(
                "link_preview_options"
            )
        );
    }

    private PublicationHttpResponse successResponse() {

        return new PublicationHttpResponse(
            200,
            """
            {
              "ok": true,
              "result": {
                "message_id": 123
              }
            }
            """
        );
    }
}
