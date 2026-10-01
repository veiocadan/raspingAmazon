package com.raspingamazon.infrastructure.config;

import java.util.Map;
import java.util.Objects;

/**
 * Carrega a configuração da caixa de staging do WhatsApp manual.
 *
 * <p>Esta configuração só deve ser carregada quando
 * {@code WHATSAPP_MANUAL_ENABLED=true}.</p>
 */
public final class WhatsAppManualStagingConfigProvider {

    static final String TELEGRAM_DESTINATION =
        "WHATSAPP_MANUAL_TELEGRAM_DESTINATION";

    private WhatsAppManualStagingConfigProvider() {
    }

    public static WhatsAppManualStagingConfig load() {

        return load(
            System.getenv()
        );
    }

    static WhatsAppManualStagingConfig load(
        Map<String, String> environment
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        String telegramDestination =
            ChannelEnvironmentConfigSupport.readRequired(
                environment,
                TELEGRAM_DESTINATION
            );

        return new WhatsAppManualStagingConfig(
            telegramDestination
        );
    }
}
