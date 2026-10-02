package com.raspingamazon.infrastructure.publication.http;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Requisição HTTP JSON utilizada para integração com
 * provedores externos de publicação.
 *
 * <p>URI e cabeçalhos são deliberadamente omitidos de
 * {@link #toString()} porque podem conter credenciais.
 * No Telegram, por exemplo, o token do bot faz parte
 * do caminho da URI. No WhatsApp, o access token será
 * enviado em cabeçalho.</p>
 */
public record PublicationHttpRequest(
    URI uri,
    Map<String, String> headers,
    String body,
    Duration requestTimeout
) {

    public PublicationHttpRequest {

        Objects.requireNonNull(
            uri,
            "uri must not be null"
        );

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

            throw new IllegalArgumentException(
                "uri must be an absolute HTTP URI"
            );
        }

        headers =
            validatedHeaders(
                headers
            );

        body =
            requireText(
                body,
                "body"
            );

        Objects.requireNonNull(
            requestTimeout,
            "requestTimeout must not be null"
        );

        if (requestTimeout.isZero()
            || requestTimeout.isNegative()) {

            throw new IllegalArgumentException(
                "requestTimeout must be positive"
            );
        }
    }

    @Override
    public String toString() {

        return "PublicationHttpRequest["
            + "uri=<redacted>"
            + ", headers=<redacted>"
            + ", bodyLength="
            + body.length()
            + ", requestTimeout="
            + requestTimeout
            + "]";
    }

    private static Map<String, String> validatedHeaders(
        Map<String, String> headers
    ) {

        Objects.requireNonNull(
            headers,
            "headers must not be null"
        );

        Map<String, String> validated =
            new LinkedHashMap<>();

        headers.forEach(
            (name, value) -> {

                String validatedName =
                    requireText(
                        name,
                        "header name"
                    );

                String validatedValue =
                    requireText(
                        value,
                        "header value"
                    );

                validated.put(
                    validatedName,
                    validatedValue
                );
            }
        );

        return Map.copyOf(
            validated
        );
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return trimmed;
    }
}
