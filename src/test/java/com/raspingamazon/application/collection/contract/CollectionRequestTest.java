package com.raspingamazon.application.collection.contract;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class CollectionRequestTest {

    @Test
    void shouldCreateValidRequest() {
        var request = new CollectionRequest(
                URI.create("https://example.com/deals")
        );

        assertEquals(
                "https://example.com/deals",
                request.source().toString()
        );
    }

    @Test
    void shouldRejectNullSource() {
        assertThrows(
                NullPointerException.class,
                () -> new CollectionRequest(null)
        );
    }

    @Test
    void shouldRejectRelativeSource() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CollectionRequest(
                        URI.create("/deals")
                )
        );
    }
}