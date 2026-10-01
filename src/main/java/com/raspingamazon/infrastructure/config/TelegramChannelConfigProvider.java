package com.raspingamazon.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Carrega a configuração do Telegram a partir de variáveis de ambiente.
 *
 * <p>Credenciais e configuração operacional permanecem fora do código.</p>
 *
 * <p>Variáveis:</p>
 *
 * <pre>
 * TELEGRAM_BOT_TOKEN
 * TELEGRAM_API_BASE_URI
 * TELEGRAM_REQUEST_TIMEOUT
 * TELEGRAM_LINK_PREVIEW_ENABLED
 * TELEGRAM_LINK_PREVIEW_POSITION
 * TELEGRAM_LINK_PREVIEW_SIZE
 * </pre>
 */
public final class TelegramChannelConfigProvider {

    static final String BOT_TOKEN =
        "TELEGRAM_BOT_TOKEN";

    static final String API_BASE_URI =
        "TELEGRAM_API_BASE_URI";

    static final String REQUEST_TIMEOUT =
        "TELEGRAM_REQUEST_TIMEOUT";

    static final String LINK_PREVIEW_ENABLED =
        "TELEGRAM_LINK_PREVIEW_ENABLED";

    static final String LINK_PREVIEW_POSITION =
        "TELEGRAM_LINK_PREVIEW_POSITION";

    static final String LINK_PREVIEW_SIZE =
        "TELEGRAM_LINK_PREVIEW_SIZE";

    private static final String DEFAULT_API_BASE_URI =
        "https://api.telegram.org";

    private static final String DEFAULT_REQUEST_TIMEOUT =
        "PT10S";

    private static final String DEFAULT_LINK_PREVIEW_ENABLED =
        "true";

    private static final String DEFAULT_LINK_PREVIEW_POSITION =
        "ABOVE";

    private static final String DEFAULT_LINK_PREVIEW_SIZE =
        "LARGE";

    private TelegramChannelConfigProvider() {
    }

    public static TelegramChannelConfig load() {

        return load(
            System.getenv()
        );
    }

    static TelegramChannelConfig load(
        Map<String, String> environment
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        String botToken =
            ChannelEnvironmentConfigSupport.readRequired(
                environment,
                BOT_TOKEN
            );

        URI apiBaseUri =
            ChannelEnvironmentConfigSupport.parseHttpUri(
                API_BASE_URI,
                ChannelEnvironmentConfigSupport.readOrDefault(
                    environment,
                    API_BASE_URI,
                    DEFAULT_API_BASE_URI
                )
            );

        Duration requestTimeout =
            ChannelEnvironmentConfigSupport.parsePositiveDuration(
                REQUEST_TIMEOUT,
                ChannelEnvironmentConfigSupport.readOrDefault(
                    environment,
                    REQUEST_TIMEOUT,
                    DEFAULT_REQUEST_TIMEOUT
                )
            );

        boolean linkPreviewEnabled =
            parseBoolean(
                ChannelEnvironmentConfigSupport.readOrDefault(
                    environment,
                    LINK_PREVIEW_ENABLED,
                    DEFAULT_LINK_PREVIEW_ENABLED
                ),
                LINK_PREVIEW_ENABLED
            );

        TelegramChannelConfig.LinkPreviewPosition linkPreviewPosition =
            parseEnum(
                ChannelEnvironmentConfigSupport.readOrDefault(
                    environment,
                    LINK_PREVIEW_POSITION,
                    DEFAULT_LINK_PREVIEW_POSITION
                ),
                LINK_PREVIEW_POSITION,
                TelegramChannelConfig.LinkPreviewPosition.class
            );

        TelegramChannelConfig.LinkPreviewSize linkPreviewSize =
            parseEnum(
                ChannelEnvironmentConfigSupport.readOrDefault(
                    environment,
                    LINK_PREVIEW_SIZE,
                    DEFAULT_LINK_PREVIEW_SIZE
                ),
                LINK_PREVIEW_SIZE,
                TelegramChannelConfig.LinkPreviewSize.class
            );

        return new TelegramChannelConfig(
            apiBaseUri,
            botToken,
            requestTimeout,
            linkPreviewEnabled,
            linkPreviewPosition,
            linkPreviewSize
        );
    }

    private static boolean parseBoolean(
        String value,
        String variableName
    ) {

        Objects.requireNonNull(
            value,
            "value must not be null"
        );

        Objects.requireNonNull(
            variableName,
            "variableName must not be null"
        );

        String normalized =
            value
                .trim()
                .toLowerCase(
                    Locale.ROOT
                );

        return switch (normalized) {

            case "true" ->
                true;

            case "false" ->
                false;

            default ->
                throw new IllegalArgumentException(
                    variableName
                        + " must be either true or false"
                );
        };
    }

    private static <E extends Enum<E>> E parseEnum(
        String value,
        String variableName,
        Class<E> enumType
    ) {

        Objects.requireNonNull(
            value,
            "value must not be null"
        );

        Objects.requireNonNull(
            variableName,
            "variableName must not be null"
        );

        Objects.requireNonNull(
            enumType,
            "enumType must not be null"
        );

        try {

            return Enum.valueOf(
                enumType,
                value
                    .trim()
                    .toUpperCase(
                        Locale.ROOT
                    )
            );

        } catch (IllegalArgumentException exception) {

            throw new IllegalArgumentException(
                variableName
                    + " has an unsupported value: "
                    + value,
                exception
            );
        }
    }
}
