package com.raspingamazon.infrastructure.publication.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Implementação do transporte HTTP de publicação baseada
 * no cliente HTTP da plataforma Java.
 *
 * <p>A classe executa POST JSON e preserva integralmente
 * status e corpo da resposta para interpretação pelo
 * adapter concreto do provedor.</p>
 */
public final class JavaPublicationHttpTransport
    implements PublicationHttpTransport {

    private static final String USER_AGENT =
        "RaspingAmazon/1.0";

    private final HttpClient httpClient;

    public JavaPublicationHttpTransport(
        HttpClient httpClient
    ) {

        this.httpClient =
            Objects.requireNonNull(
                httpClient,
                "httpClient must not be null"
            );
    }

    @Override
    public PublicationHttpResponse post(
        PublicationHttpRequest request
    ) {

        Objects.requireNonNull(
            request,
            "request must not be null"
        );

        HttpRequest.Builder builder =
            HttpRequest.newBuilder()
                .uri(
                    request.uri()
                )
                .timeout(
                    request.requestTimeout()
                )
                .setHeader(
                    "User-Agent",
                    USER_AGENT
                )
                .setHeader(
                    "Accept",
                    "application/json"
                )
                .setHeader(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                );

        request.headers()
            .forEach(
                builder::setHeader
            );

        HttpRequest httpRequest =
            builder
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        request.body(),
                        StandardCharsets.UTF_8
                    )
                )
                .build();

        try {

            HttpResponse<String> response =
                httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString(
                        StandardCharsets.UTF_8
                    )
                );

            return new PublicationHttpResponse(
                response.statusCode(),
                response.body()
            );

        } catch (InterruptedException exception) {

            Thread.currentThread()
                .interrupt();

            throw new PublicationHttpTransportException(
                "Publication HTTP request was interrupted",
                exception
            );

        } catch (IOException exception) {

            throw new PublicationHttpTransportException(
                "Publication HTTP request failed",
                exception
            );
        }
    }
}
