package com.raspingamazon.infrastructure.publication.http;

import java.util.Objects;

/**
 * Resposta HTTP bruta devolvida ao adapter do canal.
 *
 * <p>A classificação de sucesso, falha transitória ou falha
 * permanente não pertence ao transporte. Essa interpretação
 * será responsabilidade de TelegramChannel ou WhatsAppChannel.</p>
 */
public record PublicationHttpResponse(
    int statusCode,
    String body
) {

    public PublicationHttpResponse {

        if (statusCode < 100
            || statusCode > 599) {

            throw new IllegalArgumentException(
                "statusCode must be between 100 and 599"
            );
        }

        Objects.requireNonNull(
            body,
            "body must not be null"
        );
    }
}
