package com.raspingamazon.infrastructure.publication.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.infrastructure.config.WhatsAppChannelConfig;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhatsAppChannelTest {

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    private final WhatsAppChannelConfig config =
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
    void shouldPublishTemplateAndMapProviderReference()
        throws Exception {

        AtomicReference<PublicationHttpRequest> capturedRequest =
            new AtomicReference<>();

        PublicationHttpTransport transport =
            request -> {

                capturedRequest.set(
                    request
                );

                return successfulResponse();
            };

        PublicationResult result =
            new WhatsAppChannel(
                config,
                transport,
                objectMapper
            ).publish(
                new PublicationCommand(
                    20L,
                    "WHATSAPP",
                    "5511999999999",
                    "Oferta Amazon\nR$ 99,90"
                )
            );

        assertTrue(
            result.successful()
        );

        assertEquals(
            "wamid.test-message-id",
            result.providerReferenceValue()
                .orElseThrow()
        );

        PublicationHttpRequest request =
            capturedRequest.get();

        assertEquals(
            URI.create(
                "https://graph.facebook.com"
                    + "/v26.0"
                    + "/123456789012345"
                    + "/messages"
            ),
            request.uri()
        );

        assertEquals(
            "Bearer secret-access-token",
            request.headers()
                .get(
                    "Authorization"
                )
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
            "whatsapp",
            body.get(
                    "messaging_product"
                )
                .asText()
        );

        assertEquals(
            "individual",
            body.get(
                    "recipient_type"
                )
                .asText()
        );

        assertEquals(
            "5511999999999",
            body.get(
                    "to"
                )
                .asText()
        );

        assertEquals(
            "template",
            body.get(
                    "type"
                )
                .asText()
        );

        JsonNode template =
            body.get(
                "template"
            );

        assertEquals(
            "amazon_offer",
            template.get(
                    "name"
                )
                .asText()
        );

        assertEquals(
            "pt_BR",
            template.get(
                    "language"
                )
                .get(
                    "code"
                )
                .asText()
        );

        JsonNode component =
            template.get(
                    "components"
                )
                .get(
                    0
                );

        assertEquals(
            "body",
            component.get(
                    "type"
                )
                .asText()
        );

        JsonNode parameter =
            component.get(
                    "parameters"
                )
                .get(
                    0
                );

        assertEquals(
            "text",
            parameter.get(
                    "type"
                )
                .asText()
        );

        assertEquals(
            "Oferta Amazon\nR$ 99,90",
            parameter.get(
                    "text"
                )
                .asText()
        );
    }

    @Test
    void shouldNormalizeLeadingPlusInDestination()
        throws Exception {

        AtomicReference<PublicationHttpRequest> capturedRequest =
            new AtomicReference<>();

        PublicationHttpTransport transport =
            request -> {

                capturedRequest.set(
                    request
                );

                return successfulResponse();
            };

        PublicationResult result =
            new WhatsAppChannel(
                config,
                transport,
                objectMapper
            ).publish(
                new PublicationCommand(
                    20L,
                    "WHATSAPP",
                    "+5511999999999",
                    "Oferta Amazon"
                )
            );

        assertTrue(
            result.successful()
        );

        JsonNode body =
            objectMapper.readTree(
                capturedRequest.get()
                    .body()
            );

        assertEquals(
            "5511999999999",
            body.get(
                    "to"
                )
                .asText()
        );
    }

    @Test
    void shouldRejectInvalidDestinationBeforeCallingProvider() {

        AtomicInteger callCount =
            new AtomicInteger();

        PublicationHttpTransport transport =
            request -> {

                callCount.incrementAndGet();

                return successfulResponse();
            };

        PublicationResult result =
            new WhatsAppChannel(
                config,
                transport,
                objectMapper
            ).publish(
                new PublicationCommand(
                    20L,
                    "WHATSAPP",
                    "(11) 99999-9999",
                    "Oferta Amazon"
                )
            );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "WHATSAPP_INVALID_DESTINATION",
            result.errorCodeValue()
                .orElseThrow()
        );

        assertEquals(
            0,
            callCount.get()
        );
    }

    @Test
    void shouldConvertTransportFailureIntoDeliveryUnknownResult() {

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
            new WhatsAppChannel(
                config,
                transport,
                objectMapper
            ).publish(
                validCommand()
            );

        assertTrue(
            result.deliveryUnknown()
        );

        assertEquals(
            "WHATSAPP_TRANSPORT_ERROR",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldClassifyRateLimitAsTransient() {

        PublicationResult result =
            channelReturning(
                new PublicationHttpResponse(
                    429,
                    """
                    {
                      "error": {
                        "message": "Rate limited"
                      }
                    }
                    """
                )
            ).publish(
                validCommand()
            );

        assertTrue(
            result.transientFailure()
        );

        assertEquals(
            "WHATSAPP_RATE_LIMITED",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldClassifyHttpTimeoutAsDeliveryUnknown() {

        PublicationResult result =
            channelReturning(
                new PublicationHttpResponse(
                    408,
                    ""
                )
            ).publish(
                validCommand()
            );

        assertTrue(
            result.deliveryUnknown()
        );

        assertEquals(
            "WHATSAPP_PROVIDER_TIMEOUT",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldClassifyServerErrorAsDeliveryUnknown() {

        PublicationResult result =
            channelReturning(
                new PublicationHttpResponse(
                    503,
                    ""
                )
            ).publish(
                validCommand()
            );

        assertTrue(
            result.deliveryUnknown()
        );

        assertEquals(
            "WHATSAPP_PROVIDER_UNAVAILABLE",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldClassifyProviderTransientFlagAsTransient() {

        PublicationResult result =
            channelReturning(
                new PublicationHttpResponse(
                    400,
                    """
                    {
                      "error": {
                        "message": "Temporary provider problem",
                        "type": "OAuthException",
                        "code": 131000,
                        "is_transient": true
                      }
                    }
                    """
                )
            ).publish(
                validCommand()
            );

        assertTrue(
            result.transientFailure()
        );

        assertEquals(
            "WHATSAPP_PROVIDER_TRANSIENT",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldClassifyAuthenticationFailureAsPermanent() {

        PublicationResult result =
            channelReturning(
                new PublicationHttpResponse(
                    401,
                    """
                    {
                      "error": {
                        "message": "Invalid access token"
                      }
                    }
                    """
                )
            ).publish(
                validCommand()
            );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "WHATSAPP_AUTHENTICATION_FAILED",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldPreserveProviderErrorCodeForPermanentRejection() {

        PublicationResult result =
            channelReturning(
                new PublicationHttpResponse(
                    400,
                    """
                    {
                      "error": {
                        "message": "Message undeliverable",
                        "type": "OAuthException",
                        "code": 131026
                      }
                    }
                    """
                )
            ).publish(
                validCommand()
            );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "WHATSAPP_API_REJECTED_131026",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldClassifyMalformedSuccessfulResponseAsDeliveryUnknown() {

        PublicationResult result =
            channelReturning(
                new PublicationHttpResponse(
                    200,
                    "not-json"
                )
            ).publish(
                validCommand()
            );

        assertTrue(
            result.deliveryUnknown()
        );

        assertEquals(
            "WHATSAPP_INVALID_RESPONSE",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldClassifySuccessfulResponseWithoutMessageIdAsDeliveryUnknown() {

        PublicationResult result =
            channelReturning(
                new PublicationHttpResponse(
                    200,
                    """
                    {
                      "messaging_product": "whatsapp",
                      "messages": [
                        {}
                      ]
                    }
                    """
                )
            ).publish(
                validCommand()
            );

        assertTrue(
            result.deliveryUnknown()
        );

        assertEquals(
            "WHATSAPP_INVALID_RESPONSE",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldRejectNullCommand() {

        WhatsAppChannel channel =
            channelReturning(
                successfulResponse()
            );

        assertThrows(
            NullPointerException.class,
            () ->
                channel.publish(
                    null
                )
        );
    }

    private WhatsAppChannel channelReturning(
        PublicationHttpResponse response
    ) {

        return new WhatsAppChannel(
            config,
            request -> response,
            objectMapper
        );
    }

    private PublicationCommand validCommand() {

        return new PublicationCommand(
            20L,
            "WHATSAPP",
            "5511999999999",
            "Oferta Amazon"
        );
    }

    private PublicationHttpResponse successfulResponse() {

        return new PublicationHttpResponse(
            200,
            """
            {
              "messaging_product": "whatsapp",
              "contacts": [
                {
                  "input": "5511999999999",
                  "wa_id": "5511999999999"
                }
              ],
              "messages": [
                {
                  "id": "wamid.test-message-id"
                }
              ]
            }
            """
        );
    }
}
