package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelegramChannelConfigTest {

    @Test
    void shouldCreateValidConfiguration() {

        TelegramChannelConfig config =
            new TelegramChannelConfig(
                URI.create(
                    "https://api.telegram.org"
                ),
                "123456:secret-token",
                Duration.ofSeconds(
                    10
                )
            );

        assertEquals(
            URI.create(
                "https://api.telegram.org"
            ),
            config.apiBaseUri()
        );

        assertEquals(
            "123456:secret-token",
            config.botToken()
        );

        assertEquals(
            Duration.ofSeconds(
                10
            ),
            config.requestTimeout()
        );
    }

    @Test
    void shouldRejectBlankBotToken() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new TelegramChannelConfig(
                    URI.create(
                        "https://api.telegram.org"
                    ),
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
                new TelegramChannelConfig(
                    URI.create(
                        "/telegram"
                    ),
                    "123456:secret-token",
                    Duration.ofSeconds(
                        10
                    )
                )
        );
    }

    @Test
    void shouldRejectNonHttpBaseUri() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new TelegramChannelConfig(
                    URI.create(
                        "file:///telegram"
                    ),
                    "123456:secret-token",
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
                new TelegramChannelConfig(
                    URI.create(
                        "https://api.telegram.org"
                    ),
                    "123456:secret-token",
                    Duration.ZERO
                )
        );
    }

    @Test
    void shouldNotExposeBotTokenInToString() {

        String secret =
            "123456:secret-token";

        TelegramChannelConfig config =
            new TelegramChannelConfig(
                URI.create(
                    "https://api.telegram.org"
                ),
                secret,
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
}
