package com.raspingamazon.infrastructure.publication.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.infrastructure.config.WhatsAppChannelConfig;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpRequest;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpResponse;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpTransport;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpTransportException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Adapter concreto de publicação através da WhatsApp Cloud API.
 *
 * <p>O adapter utiliza um message template previamente aprovado
 * na plataforma Meta. O conteúdo já aprovado da Publication é
 * fornecido como primeiro parâmetro textual do corpo do template.</p>
 *
 * <p>O número destinatário é recebido em PublicationCommand.
 * A configuração do canal contém apenas informações do remetente
 * e da integração com a plataforma.</p>
 */
public final class WhatsAppChannel
    implements PublicationChannel {

    private static final Pattern DESTINATION_PATTERN =
        Pattern.compile(
            "\\+?[1-9]\\d{7,14}"
        );

    private static final Pattern PHONE_NUMBER_ID_PATTERN =
        Pattern.compile(
            "\\d+"
        );

    private final WhatsAppChannelConfig config;

    private final PublicationHttpTransport transport;

    private final ObjectMapper objectMapper;

    private final URI messagesUri;

    public WhatsAppChannel(
        WhatsAppChannelConfig config,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper
    ) {

        this.config =
            Objects.requireNonNull(
                config,
                "config must not be null"
            );

        this.transport =
            Objects.requireNonNull(
                transport,
                "transport must not be null"
            );

        this.objectMapper =
            Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
            );

        this.messagesUri =
            buildMessagesUri(
                config
            );
    }

    @Override
    public PublicationResult publish(
        PublicationCommand command
    ) {

        PublicationCommand validatedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        if (!isValidDestination(
            validatedCommand.destination()
        )) {

            return PublicationResult.failedPermanent(
                "WHATSAPP_INVALID_DESTINATION"
            );
        }

        final String requestBody;

        try {

            requestBody =
                objectMapper.writeValueAsString(
                    buildPayload(
                        normalizeDestination(
                            validatedCommand.destination()
                        ),
                        validatedCommand.content()
                    )
                );

        } catch (JsonProcessingException exception) {

            return PublicationResult.failedPermanent(
                "WHATSAPP_REQUEST_SERIALIZATION_ERROR"
            );
        }

        PublicationHttpRequest request =
            new PublicationHttpRequest(
                messagesUri,
                Map.of(
                    "Authorization",
                    "Bearer "
                        + config.accessToken()
                ),
                requestBody,
                config.requestTimeout()
            );

        final PublicationHttpResponse response;

        try {

            response =
                transport.post(
                    request
                );

        } catch (PublicationHttpTransportException exception) {

            return PublicationResult.failedTransient(
                "WHATSAPP_TRANSPORT_ERROR"
            );
        }

        return translateResponse(
            response
        );
    }

    private Map<String, Object> buildPayload(
        String destination,
        String content
    ) {

        Map<String, Object> parameter =
            Map.of(
                "type",
                "text",
                "text",
                content
            );

        Map<String, Object> component =
            Map.of(
                "type",
                "body",
                "parameters",
                List.of(
                    parameter
                )
            );

        Map<String, Object> language =
            Map.of(
                "code",
                config.templateLanguage()
            );

        Map<String, Object> template =
            Map.of(
                "name",
                config.templateName(),
                "language",
                language,
                "components",
                List.of(
                    component
                )
            );

        return Map.of(
            "messaging_product",
            "whatsapp",
            "recipient_type",
            "individual",
            "to",
            destination,
            "type",
            "template",
            "template",
            template
        );
    }

    private PublicationResult translateResponse(
        PublicationHttpResponse response
    ) {

        int statusCode =
            response.statusCode();

        if (statusCode >= 200
            && statusCode < 300) {

            return translateSuccess(
                response.body()
            );
        }

        return classifyFailure(
            response
        );
    }

    private PublicationResult translateSuccess(
        String responseBody
    ) {

        final JsonNode root;

        try {

            root =
                objectMapper.readTree(
                    responseBody
                );

        } catch (JsonProcessingException exception) {

            return PublicationResult.failedTransient(
                "WHATSAPP_INVALID_RESPONSE"
            );
        }

        if (root == null
            || !root.isObject()) {

            return PublicationResult.failedTransient(
                "WHATSAPP_INVALID_RESPONSE"
            );
        }

        JsonNode messagesNode =
            root.get(
                "messages"
            );

        if (messagesNode == null
            || !messagesNode.isArray()
            || messagesNode.isEmpty()) {

            return PublicationResult.failedTransient(
                "WHATSAPP_INVALID_RESPONSE"
            );
        }

        JsonNode messageIdNode =
            messagesNode
                .get(
                    0
                )
                .get(
                    "id"
                );

        if (messageIdNode == null
            || !messageIdNode.isTextual()
            || messageIdNode.asText()
            .isBlank()) {

            return PublicationResult.failedTransient(
                "WHATSAPP_INVALID_RESPONSE"
            );
        }

        return PublicationResult.success(
            messageIdNode.asText()
        );
    }

    private PublicationResult classifyFailure(
        PublicationHttpResponse response
    ) {

        int statusCode =
            response.statusCode();

        if (statusCode == 408) {

            return PublicationResult.failedTransient(
                "WHATSAPP_PROVIDER_TIMEOUT"
            );
        }

        if (statusCode == 429) {

            return PublicationResult.failedTransient(
                "WHATSAPP_RATE_LIMITED"
            );
        }

        if (statusCode >= 500
            && statusCode <= 599) {

            return PublicationResult.failedTransient(
                "WHATSAPP_PROVIDER_UNAVAILABLE"
            );
        }

        JsonNode providerError =
            readProviderError(
                response.body()
            );

        if (providerError != null
            && providerError.path(
                "is_transient"
            )
            .asBoolean(
                false
            )) {

            return PublicationResult.failedTransient(
                "WHATSAPP_PROVIDER_TRANSIENT"
            );
        }

        if (statusCode == 401
            || statusCode == 403) {

            return PublicationResult.failedPermanent(
                "WHATSAPP_AUTHENTICATION_FAILED"
            );
        }

        if (providerError != null) {

            JsonNode providerCodeNode =
                providerError.get(
                    "code"
                );

            if (providerCodeNode != null
                && providerCodeNode.isIntegralNumber()) {

                return PublicationResult.failedPermanent(
                    "WHATSAPP_API_REJECTED_"
                        + providerCodeNode.intValue()
                );
            }
        }

        return PublicationResult.failedPermanent(
            "WHATSAPP_REJECTED_"
                + statusCode
        );
    }

    private JsonNode readProviderError(
        String responseBody
    ) {

        try {

            JsonNode root =
                objectMapper.readTree(
                    responseBody
                );

            if (root == null
                || !root.isObject()) {

                return null;
            }

            JsonNode error =
                root.get(
                    "error"
                );

            if (error == null
                || !error.isObject()) {

                return null;
            }

            return error;

        } catch (JsonProcessingException exception) {

            return null;
        }
    }

    private static URI buildMessagesUri(
        WhatsAppChannelConfig config
    ) {

        if (!PHONE_NUMBER_ID_PATTERN
            .matcher(
                config.phoneNumberId()
            )
            .matches()) {

            throw new IllegalArgumentException(
                "phoneNumberId must contain digits only"
            );
        }

        URI baseUri =
            config.graphApiBaseUri();

        if (baseUri.getQuery() != null
            || baseUri.getFragment() != null) {

            throw new IllegalArgumentException(
                "graphApiBaseUri must not contain query or fragment"
            );
        }

        String base =
            baseUri.toString();

        while (base.endsWith("/")) {

            base =
                base.substring(
                    0,
                    base.length() - 1
                );
        }

        return URI.create(
            base
                + "/"
                + config.graphApiVersion()
                + "/"
                + config.phoneNumberId()
                + "/messages"
        );
    }

    private static boolean isValidDestination(
        String destination
    ) {

        return DESTINATION_PATTERN
            .matcher(
                destination
            )
            .matches();
    }

    private static String normalizeDestination(
        String destination
    ) {

        if (destination.startsWith(
            "+"
        )) {

            return destination.substring(
                1
            );
        }

        return destination;
    }
}
