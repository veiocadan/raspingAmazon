package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramLinkPreviewConfigTest {

    @Test
    void shouldUseRecommendedPreviewDefaults() {

        TelegramChannelConfig config =
            TelegramChannelConfigProvider.load(
                Map.of(
                    "TELEGRAM_BOT_TOKEN",
                    "123456:test-token"
                )
            );

        assertTrue(
            config.linkPreviewEnabled()
        );

        assertEquals(
            TelegramChannelConfig.LinkPreviewPosition.ABOVE,
            config.linkPreviewPosition()
        );

        assertEquals(
            TelegramChannelConfig.LinkPreviewSize.LARGE,
            config.linkPreviewSize()
        );
    }

    @Test
    void shouldLoadExplicitPreviewConfiguration() {

        TelegramChannelConfig config =
            TelegramChannelConfigProvider.load(
                Map.of(
                    "TELEGRAM_BOT_TOKEN",
                    "123456:test-token",
                    "TELEGRAM_LINK_PREVIEW_ENABLED",
                    "false",
                    "TELEGRAM_LINK_PREVIEW_POSITION",
                    "below",
                    "TELEGRAM_LINK_PREVIEW_SIZE",
                    "small"
                )
            );

        assertFalse(
            config.linkPreviewEnabled()
        );

        assertEquals(
            TelegramChannelConfig.LinkPreviewPosition.BELOW,
            config.linkPreviewPosition()
        );

        assertEquals(
            TelegramChannelConfig.LinkPreviewSize.SMALL,
            config.linkPreviewSize()
        );
    }

    @Test
    void shouldRejectInvalidPreviewBoolean() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                TelegramChannelConfigProvider.load(
                    Map.of(
                        "TELEGRAM_BOT_TOKEN",
                        "123456:test-token",
                        "TELEGRAM_LINK_PREVIEW_ENABLED",
                        "yes"
                    )
                )
        );
    }

    @Test
    void shouldRejectInvalidPreviewPosition() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                TelegramChannelConfigProvider.load(
                    Map.of(
                        "TELEGRAM_BOT_TOKEN",
                        "123456:test-token",
                        "TELEGRAM_LINK_PREVIEW_POSITION",
                        "CENTER"
                    )
                )
        );
    }

    @Test
    void shouldRejectInvalidPreviewSize() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                TelegramChannelConfigProvider.load(
                    Map.of(
                        "TELEGRAM_BOT_TOKEN",
                        "123456:test-token",
                        "TELEGRAM_LINK_PREVIEW_SIZE",
                        "HUGE"
                    )
                )
        );
    }
}
