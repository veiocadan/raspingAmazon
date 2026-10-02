package com.raspingamazon.infrastructure.publication.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationHttpRequestTest {

    @Test
    void shouldCreateValidRequest() {

        PublicationHttpRequest request =
            new PublicationHttpRequest(
                URI.create(
                    "https://provider.example/messages"
                ),
                Map.of(
                    "Authorization",
                    "Bearer secret"
                ),
                "{\"text\":\"offer\"}",
                Duration.ofSeconds(
                    10
                )
            );

        assertEquals(
            URI.create(
                "https://provider.example/messages"
            ),
            request.uri()
        );

        assertEquals(
            "Bearer secret",
            request.headers()
                .get(
                    "Authorization"
                )
        );

        assertEquals(
            "{\"text\":\"offer\"}",
            request.body()
        );

        assertEquals(
            Duration.ofSeconds(
                10
            ),
            request.requestTimeout()
        );
    }

    @Test
    void shouldDefensivelyCopyHeaders() {

        Map<String, String> headers =
            new HashMap<>();

        headers.put(
            "Authorization",
            "Bearer original"
        );

        PublicationHttpRequest request =
            new PublicationHttpRequest(
                URI.create(
                    "https://provider.example/messages"
                ),
                headers,
                "{}",
                Duration.ofSeconds(
                    10
                )
            );

        headers.put(
            "Authorization",
            "Bearer changed"
        );

        assertEquals(
            "Bearer original",
            request.headers()
                .get(
                    "Authorization"
                )
        );
    }

    @Test
    void shouldRejectRelativeUri() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationHttpRequest(
                    URI.create(
                        "/messages"
                    ),
                    Map.of(),
                    "{}",
                    Duration.ofSeconds(
                        10
                    )
                )
        );
    }

    @Test
    void shouldRejectNonHttpUri() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationHttpRequest(
                    URI.create(
                        "file:///messages"
                    ),
                    Map.of(),
                    "{}",
                    Duration.ofSeconds(
                        10
                    )
                )
        );
    }

    @Test
    void shouldRejectBlankBody() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationHttpRequest(
                    URI.create(
                        "https://provider.example/messages"
                    ),
                    Map.of(),
                    " ",
                    Duration.ofSeconds(
                        10
                    )
                )
        );
    }

    @Test
    void shouldRejectNonPositiveTimeout() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationHttpRequest(
                    URI.create(
                        "https://provider.example/messages"
                    ),
                    Map.of(),
                    "{}",
                    Duration.ZERO
                )
        );
    }

    @Test
    void shouldNotExposeSensitiveDataInToString() {

        String secretUri =
            "https://provider.example/bot-secret/messages";

        String secretHeader =
            "Bearer secret-access-token";

        String secretBody =
            "{\"text\":\"private-offer-content\"}";

        PublicationHttpRequest request =
            new PublicationHttpRequest(
                URI.create(
                    secretUri
                ),
                Map.of(
                    "Authorization",
                    secretHeader
                ),
                secretBody,
                Duration.ofSeconds(
                    10
                )
            );

        String representation =
            request.toString();

        assertFalse(
            representation.contains(
                secretUri
            )
        );

        assertFalse(
            representation.contains(
                secretHeader
            )
        );

        assertFalse(
            representation.contains(
                secretBody
            )
        );
    }
}
