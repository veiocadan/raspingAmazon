package com.raspingamazon.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Carrega a configuração da WhatsApp Cloud API a partir
 * do ambiente de execução.
 *
 * <p>Versão da Graph API, Phone Number ID, access token,
 * template e idioma do template são deliberadamente
 * obrigatórios.</p>
 *
 * <p>A versão da Graph API e a identidade do template não
 * possuem defaults compilados no código, pois seus ciclos
 * de vida são controlados externamente pela Meta.</p>
 */
public final class WhatsAppChannelConfigProvider {

    public static final String ACCESS_TOKEN_VARIABLE =
        "WHATSAPP_ACCESS_TOKEN";

    public static final String PHONE_NUMBER_ID_VARIABLE =
        "WHATSAPP_PHONE_NUMBER_ID";

    public static final String GRAPH_API_VERSION_VARIABLE =
        "WHATSAPP_GRAPH_API_VERSION";

    public static final String GRAPH_API_BASE_URI_VARIABLE =
        "WHATSAPP_GRAPH_API_BASE_URI";

    public static final String TEMPLATE_NAME_VARIABLE =
        "WHATSAPP_TEMPLATE_NAME";

    public static final String TEMPLATE_LANGUAGE_VARIABLE =
        "WHATSAPP_TEMPLATE_LANGUAGE";

    public static final String REQUEST_TIMEOUT_VARIABLE =
        "WHATSAPP_REQUEST_TIMEOUT";

    private static final String DEFAULT_GRAPH_API_BASE_URI =
        "https://graph.facebook.com";

    private static final String DEFAULT_REQUEST_TIMEOUT =
        "PT10S";

    private WhatsAppChannelConfigProvider() {
    }

    public static WhatsAppChannelConfig load() {

        return load(
            System.getenv()
        );
    }

    static WhatsAppChannelConfig load(
        Map<String, String> environment
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        String accessToken =
            ChannelEnvironmentConfigSupport.readRequired(
                environment,
                ACCESS_TOKEN_VARIABLE
            );

        String phoneNumberId =
            ChannelEnvironmentConfigSupport.readRequired(
                environment,
                PHONE_NUMBER_ID_VARIABLE
            );

        String graphApiVersion =
            ChannelEnvironmentConfigSupport.readRequired(
                environment,
                GRAPH_API_VERSION_VARIABLE
            );

        String templateName =
            ChannelEnvironmentConfigSupport.readRequired(
                environment,
                TEMPLATE_NAME_VARIABLE
            );

        String templateLanguage =
            ChannelEnvironmentConfigSupport.readRequired(
                environment,
                TEMPLATE_LANGUAGE_VARIABLE
            );

        URI graphApiBaseUri =
            ChannelEnvironmentConfigSupport.parseHttpUri(
                GRAPH_API_BASE_URI_VARIABLE,
                ChannelEnvironmentConfigSupport.readOrDefault(
                    environment,
                    GRAPH_API_BASE_URI_VARIABLE,
                    DEFAULT_GRAPH_API_BASE_URI
                )
            );

        Duration requestTimeout =
            ChannelEnvironmentConfigSupport.parsePositiveDuration(
                REQUEST_TIMEOUT_VARIABLE,
                ChannelEnvironmentConfigSupport.readOrDefault(
                    environment,
                    REQUEST_TIMEOUT_VARIABLE,
                    DEFAULT_REQUEST_TIMEOUT
                )
            );

        return new WhatsAppChannelConfig(
            graphApiBaseUri,
            graphApiVersion,
            phoneNumberId,
            accessToken,
            templateName,
            templateLanguage,
            requestTimeout
        );
    }
}
