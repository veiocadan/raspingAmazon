package com.raspingamazon.infrastructure.http;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.HttpTransportResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaHttpTransportTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {

        if (server != null) {
            server.stop(
                0
            );
        }
    }

    @Test
    void shouldReturnHttpStatusAndResponseBody()
        throws IOException {

        server =
            HttpServer.create(
                new InetSocketAddress(
                    0
                ),
                0
            );

        server.createContext(
            "/test",
            exchange -> {

                byte[] body =
                    "payload".getBytes(
                        StandardCharsets.UTF_8
                    );

                exchange.sendResponseHeaders(
                    200,
                    body.length
                );

                try (OutputStream output =
                         exchange.getResponseBody()) {

                    output.write(
                        body
                    );
                }
            }
        );

        server.start();

        URI uri =
            URI.create(
                "http://localhost:"
                    + server.getAddress()
                    .getPort()
                    + "/test"
            );

        var transport =
            new JavaHttpTransport(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(
                    5
                )
            );

        HttpTransportResponse response =
            transport.get(
                uri
            );

        assertEquals(
            200,
            response.statusCode()
        );

        assertEquals(
            "payload",
            response.body()
        );
    }

    @Test
    void shouldSendStableCollectionHeaders()
        throws IOException {

        server =
            HttpServer.create(
                new InetSocketAddress(
                    0
                ),
                0
            );

        server.createContext(
            "/headers",
            exchange -> {

                String userAgent =
                    exchange.getRequestHeaders()
                        .getFirst(
                            "User-Agent"
                        );

                String accept =
                    exchange.getRequestHeaders()
                        .getFirst(
                            "Accept"
                        );

                String acceptLanguage =
                    exchange.getRequestHeaders()
                        .getFirst(
                            "Accept-Language"
                        );

                String body =
                    String.join(
                        "\n",
                        userAgent,
                        accept,
                        acceptLanguage
                    );

                byte[] response =
                    body.getBytes(
                        StandardCharsets.UTF_8
                    );

                exchange.sendResponseHeaders(
                    200,
                    response.length
                );

                try (OutputStream output =
                         exchange.getResponseBody()) {

                    output.write(
                        response
                    );
                }
            }
        );

        server.start();

        URI uri =
            URI.create(
                "http://localhost:"
                    + server.getAddress()
                    .getPort()
                    + "/headers"
            );

        var transport =
            new JavaHttpTransport(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(
                    5
                )
            );

        HttpTransportResponse response =
            transport.get(
                uri
            );

        String expected =
            """
            RaspingAmazon/1.0
            text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
            pt-BR,pt;q=0.9,en-US;q=0.7,en;q=0.6""";

        assertEquals(
            200,
            response.statusCode()
        );

        assertEquals(
            expected,
            response.body()
        );
    }

    @Test
    void shouldReturnNonSuccessHttpStatusWithoutTreatingItAsTransportFailure()
        throws IOException {

        server =
            HttpServer.create(
                new InetSocketAddress(
                    0
                ),
                0
            );

        server.createContext(
            "/not-found",
            exchange -> {

                byte[] body =
                    "not found".getBytes(
                        StandardCharsets.UTF_8
                    );

                exchange.sendResponseHeaders(
                    404,
                    body.length
                );

                try (OutputStream output =
                         exchange.getResponseBody()) {

                    output.write(
                        body
                    );
                }
            }
        );

        server.start();

        URI uri =
            URI.create(
                "http://localhost:"
                    + server.getAddress()
                    .getPort()
                    + "/not-found"
            );

        var transport =
            new JavaHttpTransport(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(
                    5
                )
            );

        HttpTransportResponse response =
            transport.get(
                uri
            );

        assertEquals(
            404,
            response.statusCode()
        );

        assertEquals(
            "not found",
            response.body()
        );
    }

    @Test
    void shouldTranslateTransportFailureIntoCollectionException() {

        var transport =
            new JavaHttpTransport(
                HttpClient.newHttpClient(),
                Duration.ofMillis(
                    100
                )
            );

        URI uri =
            URI.create(
                "http://localhost:1/unavailable"
            );

        assertThrows(
            CollectionException.class,
            () -> transport.get(
                uri
            )
        );
    }

    @Test
    void shouldRejectNullHttpClient() {

        assertThrows(
            NullPointerException.class,
            () -> new JavaHttpTransport(
                null,
                Duration.ofSeconds(
                    5
                )
            )
        );
    }

    @Test
    void shouldRejectNullTimeout() {

        assertThrows(
            NullPointerException.class,
            () -> new JavaHttpTransport(
                HttpClient.newHttpClient(),
                null
            )
        );
    }

    @Test
    void shouldRejectNonPositiveTimeout() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new JavaHttpTransport(
                HttpClient.newHttpClient(),
                Duration.ZERO
            )
        );
    }

    @Test
    void shouldRejectNullUri() {

        var transport =
            new JavaHttpTransport(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(
                    5
                )
            );

        assertThrows(
            NullPointerException.class,
            () -> transport.get(
                null
            )
        );
    }
}
