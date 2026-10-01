package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhatsAppManualStagingConfigProviderTest {

    @Test
    void shouldLoadTelegramDestination() {

        WhatsAppManualStagingConfig config =
            WhatsAppManualStagingConfigProvider.load(
                Map.of(
                    "WHATSAPP_MANUAL_TELEGRAM_DESTINATION",
                    "-1001234567890"
                )
            );

        assertEquals(
            "-1001234567890",
            config.telegramDestination()
        );
    }

    @Test
    void shouldRejectMissingDestination() {

        assertThrows(
            IllegalStateException.class,
            () ->
                WhatsAppManualStagingConfigProvider.load(
                    Map.of()
                )
        );
    }

    @Test
    void shouldRejectBlankDestination() {

        assertThrows(
            IllegalStateException.class,
            () ->
                WhatsAppManualStagingConfigProvider.load(
                    Map.of(
                        "WHATSAPP_MANUAL_TELEGRAM_DESTINATION",
                        "   "
                    )
                )
        );
    }

    @Test
    void shouldRejectInvalidDestination() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                WhatsAppManualStagingConfigProvider.load(
                    Map.of(
                        "WHATSAPP_MANUAL_TELEGRAM_DESTINATION",
                        "invalid destination"
                    )
                )
        );
    }
}
