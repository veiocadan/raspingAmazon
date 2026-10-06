package com.raspingamazon.infrastructure.publication.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationCommand;
import com.raspingamazon.application.publication.channel.PublicationContentFormatter;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.infrastructure.config.TelegramChannelConfig;
import com.raspingamazon.infrastructure.publication.format.TelegramPublicationFormatter;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpRequest;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpResponse;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpTransport;
import com.raspingamazon.infrastructure.publication.http.PublicationHttpTransportException;
import com.raspingamazon.infrastructure.publication.http.PublicationRetryAfterParser;

import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter concreto de publicação para o Telegram Bot API.
 *
 * <p>Utiliza o método {@code sendMessage} e transforma o resultado
 * HTTP/Telegram em {@link PublicationResult} independente do provider.</p>
 *
 * <p>Falhas depois que a chamada HTTP foi iniciada são tratadas de
 * forma conservadora. Quando não existe confirmação suficiente de que
 * o provider rejeitou ou aceitou a mensagem, o resultado é
 * DELIVERY_UNKNOWN e não uma falha automaticamente retentável.</p>
 *
 * <p>O adapter suporta dois modos explícitos de apresentação:</p>
 *
 * <pre>
 * PLAIN
 *     -> envia o conteúdo literalmente;
 *     -> não define parse_mode.
 *
 * HTML
 *     -> converte a marcação canônica da Publication para HTML;
 *     -> envia parse_mode = HTML.
 * </pre>
 */
public final class TelegramChannel
    implements PublicationChannel {

    private static final int MAX_TEXT_CODE_POINTS = 4096;

    private static final Pattern DESTINATION_PATTERN =
        Pattern.compile("(?:-?\\d+|@[A-Za-z0-9_]+)");

    private static final Pattern TOKEN_PATTERN =
        Pattern.compile("[A-Za-z0-9:_-]+");

    private static final Pattern HTTP_URL_PATTERN =
        Pattern.compile(
            "https?://[^\\s<>]+",
            Pattern.CASE_INSENSITIVE
        );

    private final TelegramChannelConfig config;

    private final PublicationHttpTransport transport;

    private final ObjectMapper objectMapper;

    private final MessageFormat messageFormat;

    private final PublicationContentFormatter contentFormatter;

    private final Clock clock;

    private final PublicationRetryAfterParser retryAfterParser;

    public TelegramChannel(
        TelegramChannelConfig config,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper
    ) {

        this(
            config,
            transport,
            objectMapper,
            MessageFormat.PLAIN
        );
    }

    public TelegramChannel(
        TelegramChannelConfig config,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper,
        MessageFormat messageFormat
    ) {

        this(
            config,
            transport,
            objectMapper,
            messageFormat,
            Clock.systemUTC()
        );
    }

    /**
     * Construtor com Clock explícito para avaliação determinística de
     * Retry-After sem acoplar o contrato de aplicação ao relógio do
     * sistema.
     */
    public TelegramChannel(
        TelegramChannelConfig config,
        PublicationHttpTransport transport,
        ObjectMapper objectMapper,
        MessageFormat messageFormat,
        Clock clock
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

        this.messageFormat =
            Objects.requireNonNull(
                messageFormat,
                "messageFormat must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        this.retryAfterParser =
            new PublicationRetryAfterParser();

        this.contentFormatter =
            switch (messageFormat) {

                case PLAIN ->
                    content -> content;

                case HTML ->
                    new TelegramPublicationFormatter();
            };

        validateBaseUri(
            config.apiBaseUri()
        );

        if (!TOKEN_PATTERN.matcher(
            config.botToken()
        ).matches()) {

            throw new IllegalArgumentException(
                "Telegram bot token contains unsupported characters"
            );
        }
    }

    @Override
    public PublicationResult publish(
        PublicationCommand command
    ) {

        Objects.requireNonNull(
            command,
            "command must not be null"
        );

        if (!isValidDestination(
            command.destination()
        )) {

            return PublicationResult.failedPermanent(
                "TELEGRAM_INVALID_DESTINATION"
            );
        }

        if (exceedsContentLimit(
            command.content()
        )) {

            return PublicationResult.failedPermanent(
                "TELEGRAM_CONTENT_TOO_LONG"
            );
        }

        final String renderedContent;

        try {

            renderedContent =
                contentFormatter.format(
                    command.content()
                );

        } catch (IllegalArgumentException exception) {

            return PublicationResult.failedPermanent(
                "TELEGRAM_CONTENT_FORMATTING_ERROR"
            );
        }

        final String body;

        try {

            body =
                serializeRequest(
                    command,
                    renderedContent
                );

        } catch (JsonProcessingException exception) {

            return PublicationResult.failedPermanent(
                "TELEGRAM_REQUEST_SERIALIZATION_ERROR"
            );
        }

        PublicationHttpRequest request =
            new PublicationHttpRequest(
                buildSendMessageUri(),
                Map.of(),
                body,
                config.requestTimeout()
            );

        final PublicationHttpResponse response;

        try {

            response =
                transport.post(
                    request
                );

        } catch (PublicationHttpTransportException exception) {

            /*
             * O transporte não consegue provar se a falha ocorreu
             * antes ou depois de o request atravessar a fronteira
             * externa. Portanto retry automático seria inseguro.
             */
            return PublicationResult.deliveryUnknown(
                "TELEGRAM_TRANSPORT_ERROR"
            );
        }

        return interpretResponse(
            response
        );
    }

    private String serializeRequest(
        PublicationCommand command,
        String renderedContent
    ) throws JsonProcessingException {

        ObjectNode payload =
            objectMapper.createObjectNode();

        payload.put(
            "chat_id",
            command.destination()
        );

        payload.put(
            "text",
            renderedContent
        );

        if (messageFormat
            == MessageFormat.HTML) {

            payload.put(
                "parse_mode",
                "HTML"
            );
        }

        addLinkPreviewOptions(
            payload,
            command.content()
        );

        return objectMapper.writeValueAsString(
            payload
        );
    }

    private void addLinkPreviewOptions(
        ObjectNode payload,
        String content
    ) {

        Optional<String> previewUrl =
            findFirstHttpUrl(
                content
            );

        if (previewUrl.isEmpty()) {
            return;
        }

        ObjectNode options =
            payload.putObject(
                "link_preview_options"
            );

        if (!config.linkPreviewEnabled()) {

            options.put(
                "is_disabled",
                true
            );

            return;
        }

        options.put(
            "url",
            previewUrl.orElseThrow()
        );

        options.put(
            "show_above_text",
            config.linkPreviewPosition()
                == TelegramChannelConfig.LinkPreviewPosition.ABOVE
        );

        switch (config.linkPreviewSize()) {

            case LARGE ->
                options.put(
                    "prefer_large_media",
                    true
                );

            case SMALL ->
                options.put(
                    "prefer_small_media",
                    true
                );

            case DEFAULT -> {
            }
        }
    }

    private Optional<String> findFirstHttpUrl(
        String content
    ) {

        Matcher matcher =
            HTTP_URL_PATTERN.matcher(
                content
            );

        if (!matcher.find()) {
            return Optional.empty();
        }

        String candidate =
            trimTrailingPunctuation(
                matcher.group()
            );

        if (candidate.isBlank()) {
            return Optional.empty();
        }

        try {

            URI uri =
                URI.create(
                    candidate
                );

            if (!uri.isAbsolute()) {
                return Optional.empty();
            }

            String scheme =
                uri.getScheme();

            boolean supportedScheme =
                "http".equalsIgnoreCase(
                    scheme
                )
                    || "https".equalsIgnoreCase(
                    scheme
                );

            if (!supportedScheme) {
                return Optional.empty();
            }

            return Optional.of(
                candidate
            );

        } catch (IllegalArgumentException exception) {

            return Optional.empty();
        }
    }

    private String trimTrailingPunctuation(
        String candidate
    ) {

        int end =
            candidate.length();

        while (end > 0
            && isTrailingPunctuation(
                candidate.charAt(
                    end - 1
                )
            )) {

            end--;
        }

        return candidate.substring(
            0,
            end
        );
    }

    private boolean isTrailingPunctuation(
        char character
    ) {

        return character == '.'
            || character == ','
            || character == ';'
            || character == ':'
            || character == '!'
            || character == '?'
            || character == ')'
            || character == ']'
            || character == '}';
    }

    private PublicationResult interpretResponse(
        PublicationHttpResponse response
    ) {

        int statusCode =
            response.statusCode();

        if (statusCode == 429) {

            return rateLimitedResult(
                response,
                "TELEGRAM_RATE_LIMITED"
            );
        }

        if (statusCode == 408) {

            return PublicationResult.deliveryUnknown(
                "TELEGRAM_PROVIDER_TIMEOUT"
            );
        }

        if (statusCode >= 500
            && statusCode <= 599) {

            return PublicationResult.deliveryUnknown(
                "TELEGRAM_PROVIDER_UNAVAILABLE"
            );
        }

        if (statusCode < 200
            || statusCode >= 300) {

            return PublicationResult.failedPermanent(
                "TELEGRAM_REJECTED_"
                    + statusCode
            );
        }

        JsonNode responseBody =
            parseResponseBody(
                response.body()
            );

        if (responseBody == null) {

            return PublicationResult.deliveryUnknown(
                "TELEGRAM_INVALID_RESPONSE"
            );
        }

        JsonNode okNode =
            responseBody.get(
                "ok"
            );

        if (okNode == null
            || !okNode.isBoolean()) {

            return PublicationResult.deliveryUnknown(
                "TELEGRAM_INVALID_RESPONSE"
            );
        }

        if (!okNode.booleanValue()) {

            Optional<Integer> telegramErrorCode =
                extractTelegramErrorCode(
                    responseBody
                );

            if (telegramErrorCode.isPresent()) {

                /*
                 * Aqui há confirmação explícita do Telegram de que a
                 * operação foi rejeitada. Portanto é seguro aplicar a
                 * classificação conhecida do erro.
                 */
                return classifyTelegramApiError(
                    telegramErrorCode.orElseThrow(),
                    response
                );
            }

            return PublicationResult.failedPermanent(
                "TELEGRAM_API_REJECTED_UNKNOWN"
            );
        }

        JsonNode messageIdNode =
            responseBody.path(
                    "result"
                )
                .path(
                    "message_id"
                );

        if (!messageIdNode.isIntegralNumber()) {

            /*
             * O HTTP foi 2xx e ok=true, mas não existe a prova local
             * necessária para confirmar qual mensagem foi aceita.
             */
            return PublicationResult.deliveryUnknown(
                "TELEGRAM_INVALID_RESPONSE"
            );
        }

        return PublicationResult.success(
            messageIdNode.asText()
        );
    }

    private JsonNode parseResponseBody(
        String body
    ) {

        if (body == null
            || body.isBlank()) {

            return null;
        }

        try {

            return objectMapper.readTree(
                body
            );

        } catch (JsonProcessingException exception) {

            return null;
        }
    }

    private Optional<Integer> extractTelegramErrorCode(
        JsonNode responseBody
    ) {

        JsonNode errorCodeNode =
            responseBody.get(
                "error_code"
            );

        if (errorCodeNode == null
            || !errorCodeNode.isIntegralNumber()) {

            return Optional.empty();
        }

        return Optional.of(
            errorCodeNode.intValue()
        );
    }

    private PublicationResult classifyTelegramApiError(
        int errorCode,
        PublicationHttpResponse response
    ) {

        if (errorCode == 408) {

            return PublicationResult.failedTransient(
                "TELEGRAM_PROVIDER_TIMEOUT"
            );
        }

        if (errorCode == 429) {

            return rateLimitedResult(
                response,
                "TELEGRAM_RATE_LIMITED"
            );
        }

        if (errorCode >= 500
            && errorCode <= 599) {

            return PublicationResult.failedTransient(
                "TELEGRAM_PROVIDER_UNAVAILABLE"
            );
        }

        return PublicationResult.failedPermanent(
            "TELEGRAM_API_REJECTED_"
                + errorCode
        );
    }

    private PublicationResult rateLimitedResult(
        PublicationHttpResponse response,
        String errorCode
    ) {

        Optional<OffsetDateTime> retryNotBefore =
            retryAfterParser.retryNotBefore(
                response,
                OffsetDateTime.now(
                    clock
                )
            );

        if (retryNotBefore.isPresent()) {

            return PublicationResult.failedTransientWithRetryNotBefore(
                errorCode,
                retryNotBefore.orElseThrow()
            );
        }

        return PublicationResult.failedTransient(
            errorCode
        );
    }

    private URI buildSendMessageUri() {

        String base =
            config.apiBaseUri()
                .toString();

        while (base.endsWith(
            "/"
        )) {

            base =
                base.substring(
                    0,
                    base.length() - 1
                );
        }

        return URI.create(
            base
                + "/bot"
                + config.botToken()
                + "/sendMessage"
        );
    }

    private void validateBaseUri(
        URI baseUri
    ) {

        if (baseUri.getQuery()
            != null) {

            throw new IllegalArgumentException(
                "Telegram apiBaseUri must not contain a query"
            );
        }

        if (baseUri.getFragment()
            != null) {

            throw new IllegalArgumentException(
                "Telegram apiBaseUri must not contain a fragment"
            );
        }
    }

    private boolean isValidDestination(
        String destination
    ) {

        return DESTINATION_PATTERN
            .matcher(
                destination
            )
            .matches();
    }

    private boolean exceedsContentLimit(
        String content
    ) {

        int codePointCount =
            content.codePointCount(
                0,
                content.length()
            );

        return codePointCount
            > MAX_TEXT_CODE_POINTS;
    }

    public enum MessageFormat {

        PLAIN,

        HTML
    }
}
