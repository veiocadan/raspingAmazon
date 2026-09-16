package com.raspingamazon.application.collection.contract;

import java.util.Objects;

/**
 * Representa a resposta técnica recebida de uma requisição HTTP.
 *
 * <p>Este objeto ainda não representa uma CollectionResult. Ele contém
 * informações do transporte que serão avaliadas pelo Collector.</p>
 */
public record HttpTransportResponse(
        int statusCode,
        String body
) {

    public HttpTransportResponse {
        Objects.requireNonNull(
                body,
                "HTTP response body must not be null"
        );
    }
}