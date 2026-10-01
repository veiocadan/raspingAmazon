package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelegramChannelConfigProviderTest {

    @Test
    void shouldRequireBotToken() {

        Map<String, String> environment =
            Map.of();

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    TelegramChannelConfigProvider.load(
                        environment
                    )
            );

        assertEquals(
            "Required environment variable is missing: "
                + "TELEGRAM_BOT_TOKEN",
            exception.getMessage()
        );
    }

    @Test
    void shouldLoadDefaultsWithRequiredToken() {

        Map<String, String> environment =
            Map.of(
                "TELEGRAM_BOT_TOKEN",
                "123456:test-token"
            );

        TelegramChannelConfig config =
            TelegramChannelConfigProvider.load(
                environment
            );

        assertEquals(
            URI.create(
                "https://api.telegram.org"
            ),
            config.apiBaseUri()
        );

        assertEquals(
            "123456:test-token",
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
    void shouldLoadOperationalOverrides() {

        Map<String, String> environment =
            Map.of(
                "TELEGRAM_BOT_TOKEN",
                "123456:test-token",
                "TELEGRAM_API_BASE_URI",
                "http://localhost:8080",
                "TELEGRAM_REQUEST_TIMEOUT",
                "PT3S"
            );

        TelegramChannelConfig config =
            TelegramChannelConfigProvider.load(
                environment
            );

        assertEquals(
            URI.create(
                "http://localhost:8080"
            ),
            config.apiBaseUri()
        );

        assertEquals(
            Duration.ofSeconds(
                3
            ),
            config.requestTimeout()
        );
    }

    @Test
    void shouldRejectInvalidBaseUri() {

        Map<String, String> environment =
            Map.of(
                "TELEGRAM_BOT_TOKEN",
                "123456:test-token",
                "TELEGRAM_API_BASE_URI",
                "telegram-api"
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                TelegramChannelConfigProvider.load(
                    environment
                )
        );
    }

    @Test
    void shouldRejectInvalidTimeout() {

        Map<String, String> environment =
            Map.of(
                "TELEGRAM_BOT_TOKEN",
                "123456:test-token",
                "TELEGRAM_REQUEST_TIMEOUT",
                "ten-seconds"
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                TelegramChannelConfigProvider.load(
                    environment
                )
        );
    }

    @Test
    void shouldRejectNonPositiveTimeout() {

        Map<String, String> environment =
            Map.of(
                "TELEGRAM_BOT_TOKEN",
                "123456:test-token",
                "TELEGRAM_REQUEST_TIMEOUT",
                "PT0S"
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                TelegramChannelConfigProvider.load(
                    environment
                )
        );
    }
}
