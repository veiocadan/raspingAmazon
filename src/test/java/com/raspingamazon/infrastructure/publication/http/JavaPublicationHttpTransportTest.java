package com.raspingamazon.infrastructure.publication.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaPublicationHttpTransportTest {

    @Test
    void shouldPostJsonAndPreserveHttpResponse()
        throws Exception {

        AtomicReference<String> receivedMethod =
            new AtomicReference<>();

        AtomicReference<String> receivedBody =
            new AtomicReference<>();

        AtomicReference<String> receivedAuthorization =
            new AtomicReference<>();

        AtomicReference<String> receivedContentType =
            new AtomicReference<>();

        AtomicReference<String> receivedAccept =
            new AtomicReference<>();

        HttpServer server =
            HttpServer.create(
                new InetSocketAddress(
                    "localhost",
                    0
                ),
                0
            );

        server.createContext(
            "/messages",
            exchange -> {

                receivedMethod.set(
                    exchange.getRequestMethod()
                );

                receivedBody.set(
                    new String(
                        exchange.getRequestBody()
                            .readAllBytes(),
                        StandardCharsets.UTF_8
                    )
                );

                receivedAuthorization.set(
                    exchange.getRequestHeaders()
                        .getFirst(
                            "Authorization"
                        )
                );

                receivedContentType.set(
                    exchange.getRequestHeaders()
                        .getFirst(
                            "Content-Type"
                        )
                );

                receivedAccept.set(
                    exchange.getRequestHeaders()
                        .getFirst(
                            "Accept"
                        )
                );

                byte[] response =
                    "{\"id\":\"provider-reference\"}"
                        .getBytes(
                            StandardCharsets.UTF_8
                        );

                exchange.getResponseHeaders()
                    .set(
                        "Content-Type",
                        "application/json"
                    );

                exchange.sendResponseHeaders(
                    202,
                    response.length
                );

                try (OutputStream outputStream =
                         exchange.getResponseBody()) {

                    outputStream.write(
                        response
                    );
                }
            }
        );

        try {

            server.start();

            URI uri =
                URI.create(
                    "http://localhost:"
                        + server.getAddress()
                        .getPort()
                        + "/messages"
                );

            HttpClient httpClient =
                HttpClient.newBuilder()
                    .followRedirects(
                        HttpClient.Redirect.NEVER
                    )
                    .build();

            JavaPublicationHttpTransport transport =
                new JavaPublicationHttpTransport(
                    httpClient
                );

            PublicationHttpRequest request =
                new PublicationHttpRequest(
                    uri,
                    Map.of(
                        "Authorization",
                        "Bearer test-token"
                    ),
                    "{\"text\":\"offer\"}",
                    Duration.ofSeconds(
                        2
                    )
                );

            PublicationHttpResponse response =
                transport.post(
                    request
                );

            assertEquals(
                202,
                response.statusCode()
            );

            assertEquals(
                "{\"id\":\"provider-reference\"}",
                response.body()
            );

            assertEquals(
                "POST",
                receivedMethod.get()
            );

            assertEquals(
                "{\"text\":\"offer\"}",
                receivedBody.get()
            );

            assertEquals(
                "Bearer test-token",
                receivedAuthorization.get()
            );

            assertEquals(
                "application/json; charset=UTF-8",
                receivedContentType.get()
            );

            assertEquals(
                "application/json",
                receivedAccept.get()
            );

        } finally {

            server.stop(
                0
            );
        }
    }

    @Test
    void shouldRejectNullHttpClient() {

        assertThrows(
            NullPointerException.class,
            () ->
                new JavaPublicationHttpTransport(
                    null
                )
        );
    }

    @Test
    void shouldRejectNullRequest() {

        JavaPublicationHttpTransport transport =
            new JavaPublicationHttpTransport(
                HttpClient.newHttpClient()
            );

        assertThrows(
            NullPointerException.class,
            () ->
                transport.post(
                    null
                )
        );
    }
}
