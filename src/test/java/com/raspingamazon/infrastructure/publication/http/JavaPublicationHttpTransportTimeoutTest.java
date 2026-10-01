package com.raspingamazon.infrastructure.publication.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaPublicationHttpTransportTimeoutTest {

    private static final Duration REQUEST_TIMEOUT =
        Duration.ofMillis(
            100
        );

    private static final Duration SERVER_DELAY =
        Duration.ofMillis(
            500
        );

    @Test
    void shouldConvertTimeoutIntoTransportException()
        throws Exception {

        HttpServer server =
            createSlowServer();

        try {

            server.start();

            URI uri =
                URI.create(
                    "http://localhost:"
                        + server.getAddress()
                        .getPort()
                        + "/slow"
                );

            JavaPublicationHttpTransport transport =
                new JavaPublicationHttpTransport(
                    HttpClient.newHttpClient()
                );

            PublicationHttpRequest request =
                new PublicationHttpRequest(
                    uri,
                    Map.of(),
                    "{}",
                    REQUEST_TIMEOUT
                );

            PublicationHttpTransportException exception =
                assertThrows(
                    PublicationHttpTransportException.class,
                    () ->
                        transport.post(
                            request
                        )
                );

            assertEquals(
                "Publication HTTP request failed",
                exception.getMessage()
            );

            assertEquals(
                true,
                exception.getCause() != null
            );

        } finally {

            server.stop(
                0
            );
        }
    }

    private HttpServer createSlowServer()
        throws IOException {

        HttpServer server =
            HttpServer.create(
                new InetSocketAddress(
                    "localhost",
                    0
                ),
                0
            );

        server.createContext(
            "/slow",
            exchange -> {

                try {

                    Thread.sleep(
                        SERVER_DELAY.toMillis()
                    );

                } catch (InterruptedException exception) {

                    Thread.currentThread()
                        .interrupt();
                }

                byte[] response =
                    "{}".getBytes(
                        StandardCharsets.UTF_8
                    );

                try {

                    exchange.sendResponseHeaders(
                        200,
                        response.length
                    );

                    try (OutputStream outputStream =
                             exchange.getResponseBody()) {

                        outputStream.write(
                            response
                        );
                    }

                } catch (IOException ignored) {

                    /*
                     * O timeout pode fazer o cliente encerrar a conexão
                     * antes da tentativa de resposta do servidor local.
                     */
                }
            }
        );

        return server;
    }
}
