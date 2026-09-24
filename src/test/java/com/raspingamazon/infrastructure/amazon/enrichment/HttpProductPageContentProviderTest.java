package com.raspingamazon.infrastructure.amazon.enrichment;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes herméticos do provider HTTP da página individual.
 *
 * <p>Nenhum teste deste arquivo acessa a Amazon real.</p>
 */
class HttpProductPageContentProviderTest {

    @Test
    void shouldLoadProductPageContentWithAcquisitionMetadata()
        throws Exception {

        String html =
            "<html><body>produto</body></html>";

        Clock clock =
            Clock.fixed(
                Instant.parse(
                    "2026-09-23T20:00:00Z"
                ),
                ZoneOffset.UTC
            );

        try (TestHttpServer server =
                 TestHttpServer.start(
                     200,
                     html
                 )) {

            HttpProductPageContentProvider provider =
                new HttpProductPageContentProvider(
                    HttpClient.newBuilder()
                        .followRedirects(
                            HttpClient.Redirect.NORMAL
                        )
                        .build(),
                    clock
                );

            URI requestedUri =
                URI.create(
                    server.url()
                );

            ProductPageContent content =
                provider.load(
                    requestedUri
                );

            assertEquals(
                requestedUri,
                content.requestedUri()
            );

            assertEquals(
                requestedUri,
                content.resolvedUri()
            );

            assertEquals(
                html,
                content.html()
            );

            assertEquals(
                OffsetDateTime.parse(
                    "2026-09-23T20:00:00Z"
                ),
                content.collectedAt()
            );
        }
    }

    @Test
    void shouldRejectNonSuccessfulHttpStatus()
        throws Exception {

        try (TestHttpServer server =
                 TestHttpServer.start(
                     503,
                     "Service unavailable"
                 )) {

            HttpProductPageContentProvider provider =
                new HttpProductPageContentProvider(
                    HttpClient.newHttpClient()
                );

            assertThrows(
                ProductPageContentProviderException.class,
                () -> provider.load(
                    URI.create(
                        server.url()
                    )
                )
            );
        }
    }

    @Test
    void shouldRejectEmptyBody()
        throws Exception {

        try (TestHttpServer server =
                 TestHttpServer.start(
                     200,
                     "   "
                 )) {

            HttpProductPageContentProvider provider =
                new HttpProductPageContentProvider(
                    HttpClient.newHttpClient()
                );

            assertThrows(
                ProductPageContentProviderException.class,
                () -> provider.load(
                    URI.create(
                        server.url()
                    )
                )
            );
        }
    }

    private static final class TestHttpServer
        implements AutoCloseable {

        private final HttpServer server;

        private final ExecutorService executor;

        private TestHttpServer(
            HttpServer server,
            ExecutorService executor
        ) {

            this.server =
                server;

            this.executor =
                executor;
        }

        static TestHttpServer start(
            int statusCode,
            String body
        ) throws Exception {

            HttpServer server =
                HttpServer.create(
                    new InetSocketAddress(
                        "127.0.0.1",
                        0
                    ),
                    0
                );

            server.createContext(
                "/product",
                exchange -> {

                    byte[] response =
                        body.getBytes(
                            StandardCharsets.UTF_8
                        );

                    exchange.sendResponseHeaders(
                        statusCode,
                        response.length
                    );

                    try (var output =
                             exchange.getResponseBody()) {

                        output.write(
                            response
                        );
                    }
                }
            );

            ExecutorService executor =
                Executors.newCachedThreadPool();

            server.setExecutor(
                executor
            );

            server.start();

            return new TestHttpServer(
                server,
                executor
            );
        }

        String url() {

            return "http://127.0.0.1:"
                + server.getAddress()
                .getPort()
                + "/product";
        }

        @Override
        public void close() {

            server.stop(
                0
            );

            executor.shutdownNow();
        }
    }
}
