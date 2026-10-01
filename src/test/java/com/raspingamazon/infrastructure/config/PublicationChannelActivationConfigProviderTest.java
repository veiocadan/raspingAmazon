package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationChannelActivationConfigProviderTest {

    @Test
    void shouldDisableBothChannelsByDefault() {

        PublicationChannelActivationConfig config =
            PublicationChannelActivationConfigProvider.load(
                Map.of()
            );

        assertFalse(
            config.telegramEnabled()
        );

        assertFalse(
            config.whatsAppEnabled()
        );
    }

    @Test
    void shouldLoadExplicitActivationValues() {

        PublicationChannelActivationConfig config =
            PublicationChannelActivationConfigProvider.load(
                Map.of(
                    "TELEGRAM_ENABLED",
                    " true ",
                    "WHATSAPP_ENABLED",
                    "FALSE"
                )
            );

        assertTrue(
            config.telegramEnabled()
        );

        assertFalse(
            config.whatsAppEnabled()
        );
    }

    @Test
    void shouldRejectInvalidTelegramActivationValue() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                PublicationChannelActivationConfigProvider.load(
                    Map.of(
                        "TELEGRAM_ENABLED",
                        "yes"
                    )
                )
        );
    }

    @Test
    void shouldRejectInvalidWhatsAppActivationValue() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                PublicationChannelActivationConfigProvider.load(
                    Map.of(
                        "WHATSAPP_ENABLED",
                        "1"
                    )
                )
        );
    }
}
