package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhatsAppManualStagingConfigTest {

    @Test
    void shouldAcceptNumericTelegramDestination() {

        WhatsAppManualStagingConfig config =
            new WhatsAppManualStagingConfig(
                "-1001234567890"
            );

        assertEquals(
            "-1001234567890",
            config.telegramDestination()
        );
    }

    @Test
    void shouldAcceptTelegramUsernameDestination() {

        WhatsAppManualStagingConfig config =
            new WhatsAppManualStagingConfig(
                "@rasping_whatsapp_staging"
            );

        assertEquals(
            "@rasping_whatsapp_staging",
            config.telegramDestination()
        );
    }

    @Test
    void shouldTrimDestination() {

        WhatsAppManualStagingConfig config =
            new WhatsAppManualStagingConfig(
                "  -1001234567890  "
            );

        assertEquals(
            "-1001234567890",
            config.telegramDestination()
        );
    }

    @Test
    void shouldRejectBlankDestination() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WhatsAppManualStagingConfig(
                    "   "
                )
        );
    }

    @Test
    void shouldRejectInvalidDestination() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new WhatsAppManualStagingConfig(
                    "grupo whatsapp staging"
                )
        );
    }

    @Test
    void shouldRejectNullDestination() {

        assertThrows(
            NullPointerException.class,
            () ->
                new WhatsAppManualStagingConfig(
                    null
                )
        );
    }
}
