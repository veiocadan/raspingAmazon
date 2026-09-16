package com.raspingamazon.application.collection.contract;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionCollectorTest {

    @Test
    void shouldDefineCollectionContract() {
        CollectionCollector collector = request ->
                new CollectionResult(
                        "payload",
                        OffsetDateTime.parse("2026-09-16T18:00:00-03:00"),
                        request.source().toString()
                );

        var request = new CollectionRequest(
                URI.create("https://example.com/deals")
        );

        var result = collector.collect(request);

        assertEquals(
                "payload",
                result.content()
        );

        assertEquals(
                "https://example.com/deals",
                result.source()
        );
    }
}