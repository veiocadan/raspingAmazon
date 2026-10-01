package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationRateLimitConfigProviderTest {

    @Test
    void shouldUseDocumentedDefaults() {

        PublicationRateLimitConfig config =
            PublicationRateLimitConfigProvider.load(
                Map.of()
            );

        assertEquals(
            Duration.ofSeconds(
                1
            ),
            config.telegramBotApiMinimumInterval()
        );

        assertEquals(
            Duration.ofSeconds(
                1
            ),
            config.whatsAppCloudApiMinimumInterval()
        );
    }

    @Test
    void shouldLoadExplicitIntervals() {

        PublicationRateLimitConfig config =
            PublicationRateLimitConfigProvider.load(
                Map.of(
                    "PUBLICATION_RATE_LIMIT_TELEGRAM_BOT_API_MIN_INTERVAL",
                    "PT0.5S",
                    "PUBLICATION_RATE_LIMIT_WHATSAPP_CLOUD_API_MIN_INTERVAL",
                    "PT2S"
                )
            );

        assertEquals(
            Duration.ofMillis(
                500
            ),
            config.telegramBotApiMinimumInterval()
        );

        assertEquals(
            Duration.ofSeconds(
                2
            ),
            config.whatsAppCloudApiMinimumInterval()
        );
    }

    @Test
    void shouldRejectZeroTelegramInterval() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationRateLimitConfigProvider.load(
                    Map.of(
                        "PUBLICATION_RATE_LIMIT_TELEGRAM_BOT_API_MIN_INTERVAL",
                        "PT0S"
                    )
                )
        );
    }

    @Test
    void shouldRejectNegativeWhatsAppInterval() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationRateLimitConfigProvider.load(
                    Map.of(
                        "PUBLICATION_RATE_LIMIT_WHATSAPP_CLOUD_API_MIN_INTERVAL",
                        "PT-1S"
                    )
                )
        );
    }

    @Test
    void shouldRejectMalformedDuration() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationRateLimitConfigProvider.load(
                    Map.of(
                        "PUBLICATION_RATE_LIMIT_TELEGRAM_BOT_API_MIN_INTERVAL",
                        "one-second"
                    )
                )
        );
    }
}
