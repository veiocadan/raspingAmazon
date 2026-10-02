package com.raspingamazon.infrastructure.config;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Carrega a ativação operacional dos canais externos.
 *
 * <p>Variáveis reconhecidas:</p>
 *
 * <pre>
 * TELEGRAM_ENABLED
 * WHATSAPP_MANUAL_ENABLED
 * WHATSAPP_ENABLED
 * </pre>
 *
 * <p>Ausência ou valor em branco significa {@code false}.
 * Somente {@code true} e {@code false} são aceitos.</p>
 */
public final class PublicationChannelActivationConfigProvider {

    static final String TELEGRAM_ENABLED =
        "TELEGRAM_ENABLED";

    static final String WHATSAPP_MANUAL_ENABLED =
        "WHATSAPP_MANUAL_ENABLED";

    static final String WHATSAPP_ENABLED =
        "WHATSAPP_ENABLED";

    private PublicationChannelActivationConfigProvider() {
    }

    public static PublicationChannelActivationConfig load() {

        return load(
            System.getenv()
        );
    }

    static PublicationChannelActivationConfig load(
        Map<String, String> environment
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        return new PublicationChannelActivationConfig(
            readBoolean(
                environment,
                TELEGRAM_ENABLED
            ),
            readBoolean(
                environment,
                WHATSAPP_MANUAL_ENABLED
            ),
            readBoolean(
                environment,
                WHATSAPP_ENABLED
            )
        );
    }

    private static boolean readBoolean(
        Map<String, String> environment,
        String variableName
    ) {

        String rawValue =
            environment.get(
                variableName
            );

        if (rawValue == null
            || rawValue.isBlank()) {

            return false;
        }

        String normalized =
            rawValue
                .trim()
                .toLowerCase(
                    Locale.ROOT
                );

        return switch (normalized) {

            case "true" ->
                true;

            case "false" ->
                false;

            default ->
                throw new IllegalArgumentException(
                    variableName
                        + " must be either true or false"
                );
        };
    }
}
