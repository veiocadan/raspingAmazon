package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhatsAppChannelConfigTest {

    @Test
    void shouldCreateValidConfiguration() {

        WhatsAppChannelConfig config =
            validConfig();

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
    void shouldRejectBlankGraphApiVersion() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WhatsAppChannelConfig(
                    URI.create(
                        "https://graph.facebook.com"
                    ),
                    " ",
                    "123456789012345",
                    "secret-access-token",
                    "amazon_offer",
                    "pt_BR",
                    Duration.ofSeconds(
                        10
                    )
                )
        );
    }

    @Test
    void shouldRejectInvalidGraphApiVersionFormat() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WhatsAppChannelConfig(
                    URI.create(
                        "https://graph.facebook.com"
                    ),
                    "26",
                    "123456789012345",
                    "secret-access-token",
                    "amazon_offer",
                    "pt_BR",
                    Duration.ofSeconds(
                        10
                    )
                )
        );
    }

    @Test
    void shouldRejectBlankPhoneNumberId() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WhatsAppChannelConfig(
                    URI.create(
                        "https://graph.facebook.com"
                    ),
                    "v26.0",
                    " ",
                    "secret-access-token",
                    "amazon_offer",
                    "pt_BR",
                    Duration.ofSeconds(
                        10
                    )
                )
        );
    }

    @Test
    void shouldRejectBlankAccessToken() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WhatsAppChannelConfig(
                    URI.create(
                        "https://graph.facebook.com"
                    ),
                    "v26.0",
                    "123456789012345",
                    " ",
                    "amazon_offer",
                    "pt_BR",
                    Duration.ofSeconds(
                        10
                    )
                )
        );
    }

    @Test
    void shouldRejectBlankTemplateName() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WhatsAppChannelConfig(
                    URI.create(
                        "https://graph.facebook.com"
                    ),
                    "v26.0",
                    "123456789012345",
                    "secret-access-token",
                    " ",
                    "pt_BR",
                    Duration.ofSeconds(
                        10
                    )
                )
        );
    }

    @Test
    void shouldRejectBlankTemplateLanguage() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WhatsAppChannelConfig(
                    URI.create(
                        "https://graph.facebook.com"
                    ),
                    "v26.0",
                    "123456789012345",
                    "secret-access-token",
                    "amazon_offer",
                    " ",
                    Duration.ofSeconds(
                        10
                    )
                )
        );
    }

    @Test
    void shouldRejectRelativeBaseUri() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WhatsAppChannelConfig(
                    URI.create(
                        "/graph"
                    ),
                    "v26.0",
                    "123456789012345",
                    "secret-access-token",
                    "amazon_offer",
                    "pt_BR",
                    Duration.ofSeconds(
                        10
                    )
                )
        );
    }

    @Test
    void shouldRejectNonPositiveTimeout() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WhatsAppChannelConfig(
                    URI.create(
                        "https://graph.facebook.com"
                    ),
                    "v26.0",
                    "123456789012345",
                    "secret-access-token",
                    "amazon_offer",
                    "pt_BR",
                    Duration.ZERO
                )
        );
    }

    @Test
    void shouldNotExposeAccessTokenInToString() {

        String secret =
            "secret-access-token";

        WhatsAppChannelConfig config =
            new WhatsAppChannelConfig(
                URI.create(
                    "https://graph.facebook.com"
                ),
                "v26.0",
                "123456789012345",
                secret,
                "amazon_offer",
                "pt_BR",
                Duration.ofSeconds(
                    10
                )
            );

        assertFalse(
            config.toString()
                .contains(
                    secret
                )
        );
    }

    private WhatsAppChannelConfig validConfig() {

        return new WhatsAppChannelConfig(
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
    }
}
