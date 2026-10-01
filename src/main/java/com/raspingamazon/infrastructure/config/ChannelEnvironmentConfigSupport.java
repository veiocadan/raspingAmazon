package com.raspingamazon.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * Operações compartilhadas para leitura de configuração de
 * canais externos a partir de variáveis de ambiente.
 *
 * <p>A classe permanece package-private porque é detalhe de
 * infraestrutura dos providers de configuração.</p>
 */
final class ChannelEnvironmentConfigSupport {

    private ChannelEnvironmentConfigSupport() {
    }

    static String readRequired(
        Map<String, String> environment,
        String variableName
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        String value =
            environment.get(
                variableName
            );

        if (value == null
            || value.isBlank()) {

            throw new IllegalStateException(
                "Required environment variable is missing: "
                    + variableName
            );
        }

        return value.trim();
    }

    static String readOrDefault(
        Map<String, String> environment,
        String variableName,
        String defaultValue
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        Objects.requireNonNull(
            defaultValue,
            "defaultValue must not be null"
        );

        String value =
            environment.get(
                variableName
            );

        if (value == null
            || value.isBlank()) {

            return defaultValue;
        }

        return value.trim();
    }

    static Duration parsePositiveDuration(
        String variableName,
        String value
    ) {

        Objects.requireNonNull(
            variableName,
            "variableName must not be null"
        );

        Objects.requireNonNull(
            value,
            "value must not be null"
        );

        final Duration duration;

        try {

            duration =
                Duration.parse(
                    value
                );

        } catch (DateTimeParseException exception) {

            throw new IllegalStateException(
                variableName
                    + " must be a positive ISO-8601 duration",
                exception
            );
        }

        if (duration.isZero()
            || duration.isNegative()) {

            throw new IllegalStateException(
                variableName
                    + " must be a positive ISO-8601 duration"
            );
        }

        return duration;
    }

    static URI parseHttpUri(
        String variableName,
        String value
    ) {

        Objects.requireNonNull(
            variableName,
            "variableName must not be null"
        );

        Objects.requireNonNull(
            value,
            "value must not be null"
        );

        final URI uri;

        try {

            uri =
                URI.create(
                    value
                );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                variableName
                    + " must be a valid absolute HTTP URI",
                exception
            );
        }

        String scheme =
            uri.getScheme();

        boolean validScheme =
            "http".equalsIgnoreCase(
                scheme
            )
                || "https".equalsIgnoreCase(
                scheme
            );

        if (!uri.isAbsolute()
            || !validScheme
            || uri.getHost() == null
            || uri.getHost().isBlank()) {

            throw new IllegalStateException(
                variableName
                    + " must be a valid absolute HTTP URI"
            );
        }

        return uri;
    }
}
