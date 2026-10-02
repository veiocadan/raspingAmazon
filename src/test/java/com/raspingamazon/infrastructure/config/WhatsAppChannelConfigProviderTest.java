package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhatsAppChannelConfigProviderTest {

    @Test
    void shouldRequireAccessToken() {

        Map<String, String> environment =
            completeEnvironment();

        environment.remove(
            "WHATSAPP_ACCESS_TOKEN"
        );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    WhatsAppChannelConfigProvider.load(
                        environment
                    )
            );

        assertEquals(
            "Required environment variable is missing: "
                + "WHATSAPP_ACCESS_TOKEN",
            exception.getMessage()
        );
    }

    @Test
    void shouldRequirePhoneNumberId() {

        Map<String, String> environment =
            completeEnvironment();

        environment.remove(
            "WHATSAPP_PHONE_NUMBER_ID"
        );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    WhatsAppChannelConfigProvider.load(
                        environment
                    )
            );

        assertEquals(
            "Required environment variable is missing: "
                + "WHATSAPP_PHONE_NUMBER_ID",
            exception.getMessage()
        );
    }

    @Test
    void shouldRequireGraphApiVersion() {

        Map<String, String> environment =
            completeEnvironment();

        environment.remove(
            "WHATSAPP_GRAPH_API_VERSION"
        );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    WhatsAppChannelConfigProvider.load(
                        environment
                    )
            );

        assertEquals(
            "Required environment variable is missing: "
                + "WHATSAPP_GRAPH_API_VERSION",
            exception.getMessage()
        );
    }

    @Test
    void shouldRequireTemplateName() {

        Map<String, String> environment =
            completeEnvironment();

        environment.remove(
            "WHATSAPP_TEMPLATE_NAME"
        );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    WhatsAppChannelConfigProvider.load(
                        environment
                    )
            );

        assertEquals(
            "Required environment variable is missing: "
                + "WHATSAPP_TEMPLATE_NAME",
            exception.getMessage()
        );
    }

    @Test
    void shouldRequireTemplateLanguage() {

        Map<String, String> environment =
            completeEnvironment();

        environment.remove(
            "WHATSAPP_TEMPLATE_LANGUAGE"
        );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    WhatsAppChannelConfigProvider.load(
                        environment
                    )
            );

        assertEquals(
            "Required environment variable is missing: "
                + "WHATSAPP_TEMPLATE_LANGUAGE",
            exception.getMessage()
        );
    }

    @Test
    void shouldLoadDefaultsWithRequiredConfiguration() {

        Map<String, String> environment =
            completeEnvironment();

        WhatsAppChannelConfig config =
            WhatsAppChannelConfigProvider.load(
                environment
            );

        assertEquals(
            URI.create(
                "https://graph.facebook.com"
            ),
            config.graphApiBaseUri()
        );

        assertEquals(
            "v26.0",
            config.graphApiVersion()
        );

        assertEquals(
            "123456789012345",
            config.phoneNumberId()
        );

        assertEquals(
            "secret-access-token",
            config.accessToken()
        );

        assertEquals(
            "amazon_offer",
            config.templateName()
        );

        assertEquals(
            "pt_BR",
            config.templateLanguage()
        );

        assertEquals(
            Duration.ofSeconds(
                10
            ),
            config.requestTimeout()
        );
    }

    @Test
    void shouldLoadOperationalOverrides() {

        Map<String, String> environment =
            completeEnvironment();

        environment.put(
            "WHATSAPP_GRAPH_API_VERSION",
            "v25.0"
        );

        environment.put(
            "WHATSAPP_GRAPH_API_BASE_URI",
            "http://localhost:8080"
        );

        environment.put(
            "WHATSAPP_TEMPLATE_NAME",
            "custom_offer"
        );

        environment.put(
            "WHATSAPP_TEMPLATE_LANGUAGE",
            "en_US"
        );

        environment.put(
            "WHATSAPP_REQUEST_TIMEOUT",
            "PT4S"
        );

        WhatsAppChannelConfig config =
            WhatsAppChannelConfigProvider.load(
                environment
            );

        assertEquals(
            URI.create(
                "http://localhost:8080"
            ),
            config.graphApiBaseUri()
        );

        assertEquals(
            "v25.0",
            config.graphApiVersion()
        );

        assertEquals(
            "custom_offer",
            config.templateName()
        );

        assertEquals(
            "en_US",
            config.templateLanguage()
        );

        assertEquals(
            Duration.ofSeconds(
                4
            ),
            config.requestTimeout()
        );
    }

    @Test
    void shouldRejectInvalidGraphApiVersion() {

        Map<String, String> environment =
            completeEnvironment();

        environment.put(
            "WHATSAPP_GRAPH_API_VERSION",
            "latest"
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                WhatsAppChannelConfigProvider.load(
                    environment
                )
        );
    }

    @Test
    void shouldRejectInvalidBaseUri() {

        Map<String, String> environment =
            completeEnvironment();

        environment.put(
            "WHATSAPP_GRAPH_API_BASE_URI",
            "graph-api"
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                WhatsAppChannelConfigProvider.load(
                    environment
                )
        );
    }

    @Test
    void shouldRejectInvalidTimeout() {

        Map<String, String> environment =
            completeEnvironment();

        environment.put(
            "WHATSAPP_REQUEST_TIMEOUT",
            "ten-seconds"
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                WhatsAppChannelConfigProvider.load(
                    environment
                )
        );
    }

    @Test
    void shouldRejectNonPositiveTimeout() {

        Map<String, String> environment =
            completeEnvironment();

        environment.put(
            "WHATSAPP_REQUEST_TIMEOUT",
            "PT0S"
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                WhatsAppChannelConfigProvider.load(
                    environment
                )
        );
    }

    private Map<String, String> completeEnvironment() {

        Map<String, String> environment =
            new HashMap<>();

        environment.put(
            "WHATSAPP_ACCESS_TOKEN",
            "secret-access-token"
        );

        environment.put(
            "WHATSAPP_PHONE_NUMBER_ID",
            "123456789012345"
        );

        environment.put(
            "WHATSAPP_GRAPH_API_VERSION",
            "v26.0"
        );

        environment.put(
            "WHATSAPP_TEMPLATE_NAME",
            "amazon_offer"
        );

        environment.put(
            "WHATSAPP_TEMPLATE_LANGUAGE",
            "pt_BR"
        );

        return environment;
    }
}
