package com.raspingamazon.infrastructure.config;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Configuração da caixa privada de staging utilizada para
 * publicações manuais no WhatsApp.
 *
 * <p>O conteúdo destinado ao WhatsApp é entregue inicialmente
 * em um chat/canal privado do Telegram. A publicação final no
 * WhatsApp permanece uma ação humana.</p>
 */
public record WhatsAppManualStagingConfig(
    String telegramDestination
) {

    private static final Pattern TELEGRAM_DESTINATION_PATTERN =
        Pattern.compile(
            "(?:-?\\d+|@[A-Za-z0-9_]+)"
        );

    public WhatsAppManualStagingConfig {

        Objects.requireNonNull(
            telegramDestination,
            "telegramDestination must not be null"
        );

        telegramDestination =
            telegramDestination.trim();

        if (telegramDestination.isEmpty()) {

            throw new IllegalArgumentException(
                "telegramDestination must not be blank"
            );
        }

        if (!TELEGRAM_DESTINATION_PATTERN
            .matcher(
                telegramDestination
            )
            .matches()) {

            throw new IllegalArgumentException(
                "telegramDestination must be a valid Telegram destination"
            );
        }
    }
}
