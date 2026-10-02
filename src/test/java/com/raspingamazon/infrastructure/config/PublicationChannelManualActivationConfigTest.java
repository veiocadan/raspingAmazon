package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationChannelManualActivationConfigTest {

    @Test
    void shouldDisableManualWhatsAppByDefault() {

        PublicationChannelActivationConfig config =
            PublicationChannelActivationConfigProvider.load(
                Map.of()
            );

        assertFalse(
            config.whatsAppManualEnabled()
        );
    }

    @Test
    void shouldEnableManualWhatsAppExplicitly() {

        PublicationChannelActivationConfig config =
            PublicationChannelActivationConfigProvider.load(
                Map.of(
                    "WHATSAPP_MANUAL_ENABLED",
                    "true"
                )
            );

        assertTrue(
            config.whatsAppManualEnabled()
        );

        assertFalse(
            config.telegramEnabled()
        );

        assertFalse(
            config.whatsAppEnabled()
        );
    }

    @Test
    void shouldRejectInvalidManualWhatsAppActivationValue() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                PublicationChannelActivationConfigProvider.load(
                    Map.of(
                        "WHATSAPP_MANUAL_ENABLED",
                        "yes"
                    )
                )
        );
    }

    @Test
    void legacyConstructorShouldKeepManualWhatsAppDisabled() {

        PublicationChannelActivationConfig config =
            new PublicationChannelActivationConfig(
                true,
                false
            );

        assertTrue(
            config.telegramEnabled()
        );

        assertFalse(
            config.whatsAppManualEnabled()
        );

        assertFalse(
            config.whatsAppEnabled()
        );
    }
}
