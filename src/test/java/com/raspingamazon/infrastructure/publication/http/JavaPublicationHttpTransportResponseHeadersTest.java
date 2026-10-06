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

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaPublicationHttpTransportResponseHeadersTest {

    @Test
    void shouldPreserveRetryAfterResponseHeader()
        throws Exception {

        HttpServer server =
            HttpServer.create(
                new InetSocketAddress(
                    "localhost",
                    0
                ),
                0
            );

        server.createContext(
            "/rate-limited",
            exchange -> {

                byte[] responseBody =
                    "{\"error\":\"rate_limited\"}"
                        .getBytes(
                            StandardCharsets.UTF_8
                        );

                exchange.getResponseHeaders()
                    .set(
                        "Retry-After",
                        "120"
                    );

                exchange.getResponseHeaders()
                    .set(
                        "X-Provider-Request-Id",
                        "provider-request-123"
                    );

                exchange.sendResponseHeaders(
                    429,
                    responseBody.length
                );

                try (OutputStream outputStream =
                         exchange.getResponseBody()) {

                    outputStream.write(
                        responseBody
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
                        + "/rate-limited"
                );

            PublicationHttpRequest request =
                new PublicationHttpRequest(
                    uri,
                    Map.of(),
                    "{}",
                    Duration.ofSeconds(
                        2
                    )
                );

            PublicationHttpResponse response =
                new JavaPublicationHttpTransport(
                    HttpClient.newHttpClient()
                ).post(
                    request
                );

            assertEquals(
                429,
                response.statusCode()
            );

            assertEquals(
                "120",
                response.firstHeaderValue(
                        "Retry-After"
                    )
                    .orElseThrow()
            );

            assertEquals(
                "provider-request-123",
                response.firstHeaderValue(
                        "X-Provider-Request-Id"
                    )
                    .orElseThrow()
            );

        } finally {

            server.stop(
                0
            );
        }
    }
}
