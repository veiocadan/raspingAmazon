package com.raspingamazon.infrastructure.publication.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.infrastructure.config.TelegramChannelConfig;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramChannelTest {

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    private final TelegramChannelConfig config =
        new TelegramChannelConfig(
            URI.create(
                "https://api.telegram.org"
            ),
            "123456:test-token",
            Duration.ofSeconds(
                10
            )
        );

    @Test
    void shouldPublishPlainTextAndMapMessageId()
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
                        "message_id": 345
                      }
                    }
                    """
                );
            };

        TelegramChannel channel =
            new TelegramChannel(
                config,
                transport,
                objectMapper
            );

        PublicationCommand command =
            new PublicationCommand(
                10L,
                "TELEGRAM",
                "@offers_channel",
                "Oferta Amazon\nR$ 99,90"
            );

        PublicationResult result =
            channel.publish(
                command
            );

        assertTrue(
            result.successful()
        );

        assertEquals(
            "345",
            result.providerReferenceValue()
                .orElseThrow()
        );

        assertTrue(
            result.errorCodeValue()
                .isEmpty()
        );

        PublicationHttpRequest request =
            capturedRequest.get();

        assertEquals(
            URI.create(
                "https://api.telegram.org"
                    + "/bot123456:test-token"
                    + "/sendMessage"
            ),
            request.uri()
        );

        assertTrue(
            request.headers()
                .isEmpty()
        );

        assertEquals(
            Duration.ofSeconds(
                10
            ),
            request.requestTimeout()
        );

        JsonNode body =
            objectMapper.readTree(
                request.body()
            );

        assertEquals(
            "@offers_channel",
            body.get(
                    "chat_id"
                )
                .asText()
        );

        assertEquals(
            "Oferta Amazon\nR$ 99,90",
            body.get(
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
    void shouldClassifyHttpRateLimitAsTransient() {

        TelegramChannel channel =
            channelReturning(
                new PublicationHttpResponse(
                    429,
                    """
                    {
                      "ok": false,
                      "error_code": 429,
                      "description": "Too Many Requests"
                    }
                    """
                )
            );

        PublicationResult result =
            channel.publish(
                validCommand()
            );

        assertTrue(
            result.transientFailure()
        );

        assertEquals(
            "TELEGRAM_RATE_LIMITED",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldClassifyServerErrorAsTransient() {

        TelegramChannel channel =
            channelReturning(
                new PublicationHttpResponse(
                    503,
                    ""
                )
            );

        PublicationResult result =
            channel.publish(
                validCommand()
            );

        assertTrue(
            result.transientFailure()
        );

        assertEquals(
            "TELEGRAM_PROVIDER_UNAVAILABLE",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldClassifyClientErrorAsPermanent() {

        TelegramChannel channel =
            channelReturning(
                new PublicationHttpResponse(
                    403,
                    """
                    {
                      "ok": false,
                      "error_code": 403,
                      "description": "Forbidden"
                    }
                    """
                )
            );

        PublicationResult result =
            channel.publish(
                validCommand()
            );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "TELEGRAM_REJECTED_403",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldClassifyApiRateLimitInsideSuccessfulHttpStatusAsTransient() {

        TelegramChannel channel =
            channelReturning(
                new PublicationHttpResponse(
                    200,
                    """
                    {
                      "ok": false,
                      "error_code": 429,
                      "description": "Too Many Requests",
                      "parameters": {
                        "retry_after": 30
                      }
                    }
                    """
                )
            );

        PublicationResult result =
            channel.publish(
                validCommand()
            );

        assertTrue(
            result.transientFailure()
        );

        assertEquals(
            "TELEGRAM_RATE_LIMITED",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldConvertTransportFailureIntoTransientResult() {

        PublicationHttpTransport transport =
            request -> {

                throw new PublicationHttpTransportException(
                    "test transport failure",
                    new IOException(
                        "connection unavailable"
                    )
                );
            };

        TelegramChannel channel =
            new TelegramChannel(
                config,
                transport,
                objectMapper
            );

        PublicationResult result =
            channel.publish(
                validCommand()
            );

        assertTrue(
            result.transientFailure()
        );

        assertEquals(
            "TELEGRAM_TRANSPORT_ERROR",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldClassifyMalformedSuccessfulResponseAsTransient() {

        TelegramChannel channel =
            channelReturning(
                new PublicationHttpResponse(
                    200,
                    "not-json"
                )
            );

        PublicationResult result =
            channel.publish(
                validCommand()
            );

        assertTrue(
            result.transientFailure()
        );

        assertEquals(
            "TELEGRAM_INVALID_RESPONSE",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldRejectInvalidDestinationBeforeCallingProvider() {

        AtomicInteger callCount =
            new AtomicInteger();

        PublicationHttpTransport transport =
            request -> {

                callCount.incrementAndGet();

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
            };

        TelegramChannel channel =
            new TelegramChannel(
                config,
                transport,
                objectMapper
            );

        PublicationCommand command =
            new PublicationCommand(
                10L,
                "TELEGRAM",
                "invalid destination",
                "Oferta"
            );

        PublicationResult result =
            channel.publish(
                command
            );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "TELEGRAM_INVALID_DESTINATION",
            result.errorCodeValue()
                .orElseThrow()
        );

        assertEquals(
            0,
            callCount.get()
        );
    }

    @Test
    void shouldRejectContentAboveTelegramLimitBeforeCallingProvider() {

        AtomicInteger callCount =
            new AtomicInteger();

        PublicationHttpTransport transport =
            request -> {

                callCount.incrementAndGet();

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
            };

        TelegramChannel channel =
            new TelegramChannel(
                config,
                transport,
                objectMapper
            );

        PublicationCommand command =
            new PublicationCommand(
                10L,
                "TELEGRAM",
                "@offers_channel",
                "a".repeat(
                    4097
                )
            );

        PublicationResult result =
            channel.publish(
                command
            );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "TELEGRAM_CONTENT_TOO_LONG",
            result.errorCodeValue()
                .orElseThrow()
        );

        assertEquals(
            0,
            callCount.get()
        );
    }

    @Test
    void shouldRejectNullCommand() {

        TelegramChannel channel =
            channelReturning(
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
                )
            );

        assertThrows(
            NullPointerException.class,
            () ->
                channel.publish(
                    null
                )
        );
    }

    private TelegramChannel channelReturning(
        PublicationHttpResponse response
    ) {

        return new TelegramChannel(
            config,
            request -> response,
            objectMapper
        );
    }

    private PublicationCommand validCommand() {

        return new PublicationCommand(
            10L,
            "TELEGRAM",
            "@offers_channel",
            "Oferta Amazon"
        );
    }
}
