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
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes da implementação de transporte HTTP baseada na JDK.
 *
 * <p>O servidor HTTP utilizado aqui é local e controlado pelo teste.
 * Dessa forma, validamos o comportamento do transporte sem depender
 * da Amazon, da internet ou de serviços externos.</p>
 *
 * <p>Esta classe testa somente transporte HTTP. Não há parsing,
 * interpretação de ofertas ou regras de negócio.</p>
 */
class JavaHttpTransportTest {

    private HttpServer server;

    /**
     * Encerra o servidor local depois de cada teste.
     *
     * <p>Isso evita que uma execução deixe uma porta ocupada ou
     * interfira nos testes seguintes.</p>
     */
    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldReturnHttpStatusAndResponseBody() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        server.createContext(
                "/test",
                exchange -> {
                    byte[] body = "payload".getBytes();

                    exchange.sendResponseHeaders(
                            200,
                            body.length
                    );

                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                }
        );

        server.start();

        URI uri = URI.create(
                "http://localhost:" + server.getAddress().getPort() + "/test"
        );

        var transport = new JavaHttpTransport(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(5)
        );

        HttpTransportResponse response = transport.get(uri);

        assertEquals(200, response.statusCode());
        assertEquals("payload", response.body());
    }

    @Test
    void shouldSendStableRaspingAmazonUserAgent() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        server.createContext(
                "/user-agent",
                exchange -> {
                    String userAgent =
                            exchange.getRequestHeaders()
                                    .getFirst("User-Agent");

                    byte[] body =
                            userAgent == null
                                    ? new byte[0]
                                    : userAgent.getBytes();

                    exchange.sendResponseHeaders(
                            200,
                            body.length
                    );

                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                }
        );

        server.start();

        URI uri = URI.create(
                "http://localhost:" + server.getAddress().getPort()
                        + "/user-agent"
        );

        var transport = new JavaHttpTransport(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(5)
        );

        HttpTransportResponse response = transport.get(uri);

        /*
         * O User-Agent faz parte da identificação estável do cliente
         * de coleta. O valor deve permanecer determinístico para que
         * a execução seja reproduzível.
         */
        assertEquals(
                200,
                response.statusCode()
        );

        assertEquals(
                "RaspingAmazon/1.0",
                response.body()
        );
    }

    @Test
    void shouldReturnNonSuccessHttpStatusWithoutTreatingItAsTransportFailure()
            throws IOException {

        server = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        server.createContext(
                "/not-found",
                exchange -> {
                    byte[] body = "not found".getBytes();

                    exchange.sendResponseHeaders(
                            404,
                            body.length
                    );

                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                }
        );

        server.start();

        URI uri = URI.create(
                "http://localhost:" + server.getAddress().getPort()
                        + "/not-found"
        );

        var transport = new JavaHttpTransport(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(5)
        );

        HttpTransportResponse response = transport.get(uri);

        /*
         * Um status HTTP como 404 ainda é uma resposta HTTP válida.
         * A decisão sobre o significado desse status pertence à camada
         * de coleta, não ao transporte.
         */
        assertEquals(404, response.statusCode());
        assertEquals("not found", response.body());
    }

    @Test
    void shouldTranslateTransportFailureIntoCollectionException() {
        var transport = new JavaHttpTransport(
                HttpClient.newHttpClient(),
                Duration.ofMillis(100)
        );

        URI uri = URI.create(
                "http://localhost:1/unavailable"
        );

        assertThrows(
                CollectionException.class,
                () -> transport.get(uri)
        );
    }

    @Test
    void shouldRejectNullHttpClient() {
        assertThrows(
                NullPointerException.class,
                () -> new JavaHttpTransport(
                        null,
                        Duration.ofSeconds(5)
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
        var transport = new JavaHttpTransport(
                HttpClient.newHttpClient(),
                Duration.ofSeconds(5)
        );

        assertThrows(
                NullPointerException.class,
                () -> transport.get(null)
        );
    }
}
