package com.raspingamazon.infrastructure.publication.http;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationRetryAfterParserTest {

    private static final OffsetDateTime OBSERVED_AT =
        OffsetDateTime.parse(
            "2026-10-04T02:00:00Z"
        );

    private final PublicationRetryAfterParser parser =
        new PublicationRetryAfterParser();

    @Test
    void shouldParseDelaySecondsAsAbsoluteNotBeforeInstant() {

        PublicationHttpResponse response =
            responseWithRetryAfter(
                "120"
            );

        assertEquals(
            OBSERVED_AT.plusSeconds(
                120
            ),
            parser.retryNotBefore(
                    response,
                    OBSERVED_AT
                )
                .orElseThrow()
        );
    }

    @Test
    void shouldParseHttpDate() {

        PublicationHttpResponse response =
            responseWithRetryAfter(
                "Sun, 04 Oct 2026 03:00:00 GMT"
            );

        assertEquals(
            OffsetDateTime.parse(
                "2026-10-04T03:00:00Z"
            ),
            parser.retryNotBefore(
                    response,
                    OBSERVED_AT
                )
                .orElseThrow()
        );
    }

    @Test
    void shouldClampPastHttpDateToObservedAt() {

        PublicationHttpResponse response =
            responseWithRetryAfter(
                "Sun, 04 Oct 2026 01:00:00 GMT"
            );

        assertEquals(
            OBSERVED_AT,
            parser.retryNotBefore(
                    response,
                    OBSERVED_AT
                )
                .orElseThrow()
        );
    }

    @Test
    void shouldReturnEmptyWhenHeaderIsMissing() {

        PublicationHttpResponse response =
            new PublicationHttpResponse(
                429,
                "{}"
            );

        assertTrue(
            parser.retryNotBefore(
                    response,
                    OBSERVED_AT
                )
                .isEmpty()
        );
    }

    @Test
    void shouldIgnoreMalformedRetryAfter() {

        PublicationHttpResponse response =
            responseWithRetryAfter(
                "not-a-delay-or-http-date"
            );

        assertTrue(
            parser.retryNotBefore(
                    response,
                    OBSERVED_AT
                )
                .isEmpty()
        );
    }

    @Test
    void shouldChooseMostConservativeValidValue() {

        PublicationHttpResponse response =
            new PublicationHttpResponse(
                429,
                "{}",
                Map.of(
                    "Retry-After",
                    List.of(
                        "60",
                        "Sun, 04 Oct 2026 02:05:00 GMT",
                        "invalid"
                    )
                )
            );

        assertEquals(
            OffsetDateTime.parse(
                "2026-10-04T02:05:00Z"
            ),
            parser.retryNotBefore(
                    response,
                    OBSERVED_AT
                )
                .orElseThrow()
        );
    }

    private PublicationHttpResponse responseWithRetryAfter(
        String value
    ) {

        return new PublicationHttpResponse(
            429,
            "{}",
            Map.of(
                "Retry-After",
                List.of(
                    value
                )
            )
        );
    }
}
