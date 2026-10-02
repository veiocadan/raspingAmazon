package com.raspingamazon.infrastructure.publication.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationHttpResponseTest {

    @Test
    void shouldCreateValidResponse() {

        PublicationHttpResponse response =
            new PublicationHttpResponse(
                200,
                "{\"ok\":true}"
            );

        assertEquals(
            200,
            response.statusCode()
        );

        assertEquals(
            "{\"ok\":true}",
            response.body()
        );
    }

    @Test
    void shouldRejectStatusCodeBelowHttpRange() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationHttpResponse(
                    99,
                    ""
                )
        );
    }

    @Test
    void shouldRejectStatusCodeAboveHttpRange() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationHttpResponse(
                    600,
                    ""
                )
        );
    }

    @Test
    void shouldRejectNullBody() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationHttpResponse(
                    200,
                    null
                )
        );
    }
}
