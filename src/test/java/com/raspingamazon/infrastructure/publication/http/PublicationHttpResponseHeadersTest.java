package com.raspingamazon.infrastructure.publication.http;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationHttpResponseHeadersTest {

    @Test
    void shouldExposeHeadersCaseInsensitivelyAndPreserveAllValues() {

        PublicationHttpResponse response =
            new PublicationHttpResponse(
                429,
                "{}",
                Map.of(
                    "Retry-After",
                    List.of(
                        "120",
                        "240"
                    ),
                    "X-Request-Id",
                    List.of(
                        "request-123"
                    )
                )
            );

        assertEquals(
            List.of(
                "120",
                "240"
            ),
            response.headerValues(
                "retry-after"
            )
        );

        assertEquals(
            "request-123",
            response.firstHeaderValue(
                    "X-REQUEST-ID"
                )
                .orElseThrow()
        );
    }

    @Test
    void compatibilityConstructorShouldExposeEmptyHeaders() {

        PublicationHttpResponse response =
            new PublicationHttpResponse(
                200,
                "{}"
            );

        assertTrue(
            response.headers()
                .isEmpty()
        );

        assertTrue(
            response.firstHeaderValue(
                    "Retry-After"
                )
                .isEmpty()
        );
    }

    @Test
    void shouldDefensivelyCopyHeaderMapAndValues() {

        List<String> values =
            new ArrayList<>(
                List.of(
                    "120"
                )
            );

        Map<String, List<String>> headers =
            new LinkedHashMap<>();

        headers.put(
            "Retry-After",
            values
        );

        PublicationHttpResponse response =
            new PublicationHttpResponse(
                429,
                "{}",
                headers
            );

        values.add(
            "240"
        );

        headers.put(
            "X-New-Header",
            List.of(
                "new"
            )
        );

        assertEquals(
            List.of(
                "120"
            ),
            response.headerValues(
                "Retry-After"
            )
        );

        assertTrue(
            response.headerValues(
                    "X-New-Header"
                )
                .isEmpty()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () ->
                response.headers()
                    .put(
                        "other",
                        List.of(
                            "value"
                        )
                    )
        );
    }
}
