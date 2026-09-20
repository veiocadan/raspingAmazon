package com.raspingamazon.infrastructure.collection;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.HttpTransport;
import com.raspingamazon.application.collection.contract.HttpTransportResponse;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpCollectionCollectorTest {

    private static final URI SOURCE =
        URI.create(
            "https://example.com/deals"
        );

    private static final Instant COLLECTION_INSTANT =
        Instant.parse(
            "2026-09-16T22:00:00Z"
        );

    private static final Clock FIXED_CLOCK =
        Clock.fixed(
            COLLECTION_INSTANT,
            ZoneOffset.UTC
        );

    @Test
    void shouldTransformHttpResponseIntoCollectionResult() {

        HttpTransport transport =
            uri -> {

                assertEquals(
                    SOURCE,
                    uri
                );

                return new HttpTransportResponse(
                    200,
                    "payload"
                );
            };

        var collector =
            new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
            );

        var result =
            collector.collect(
                new CollectionRequest(
                    SOURCE
                )
            );

        assertEquals(
            "payload",
            result.content()
        );

        assertEquals(
            COLLECTION_INSTANT,
            result.collectedAt()
                .toInstant()
        );

        assertEquals(
            SOURCE.toString(),
            result.source()
        );
    }

    @Test
    void shouldPreserveHttpFailureStatusAndBodyExcerpt() {

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    503,
                    "<html>temporarily unavailable</html>"
                );

        var collector =
            new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
            );

        CollectionException exception =
            assertThrows(
                CollectionException.class,
                () -> collector.collect(
                    new CollectionRequest(
                        SOURCE
                    )
                )
            );

        assertEquals(
            503,
            exception.httpStatusCode()
        );

        assertEquals(
            "<html>temporarily unavailable</html>",
            exception.responseBodyExcerpt()
        );
    }

    @Test
    void shouldLimitHttpFailureBodyExcerpt() {

        String longBody =
            "x".repeat(
                3000
            );

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    503,
                    longBody
                );

        var collector =
            new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
            );

        CollectionException exception =
            assertThrows(
                CollectionException.class,
                () -> collector.collect(
                    new CollectionRequest(
                        SOURCE
                    )
                )
            );

        assertEquals(
            2000,
            exception.responseBodyExcerpt()
                .length()
        );
    }

    @Test
    void shouldPropagateCollectionExceptionFromTransport() {

        var expectedException =
            new CollectionException(
                "HTTP request failed"
            );

        HttpTransport transport =
            uri -> {
                throw expectedException;
            };

        var collector =
            new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
            );

        CollectionException exception =
            assertThrows(
                CollectionException.class,
                () -> collector.collect(
                    new CollectionRequest(
                        SOURCE
                    )
                )
            );

        assertEquals(
            expectedException,
            exception
        );

        assertNull(
            exception.httpStatusCode()
        );
    }

    @Test
    void shouldRejectNullRequest() {

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    200,
                    "payload"
                );

        var collector =
            new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
            );

        assertThrows(
            NullPointerException.class,
            () -> collector.collect(
                null
            )
        );
    }

    @Test
    void shouldRejectNullTransport() {

        assertThrows(
            NullPointerException.class,
            () -> new HttpCollectionCollector(
                null,
                FIXED_CLOCK
            )
        );
    }

    @Test
    void shouldRejectNullClock() {

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    200,
                    "payload"
                );

        assertThrows(
            NullPointerException.class,
            () -> new HttpCollectionCollector(
                transport,
                null
            )
        );
    }
}
