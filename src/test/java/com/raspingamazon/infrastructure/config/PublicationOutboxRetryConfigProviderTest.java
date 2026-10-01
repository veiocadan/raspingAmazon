package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationOutboxRetryConfigProviderTest {

    @Test
    void shouldUseDocumentedDefaultsWhenEnvironmentIsAbsent() {

        PublicationOutboxRetryConfig config =
            PublicationOutboxRetryConfigProvider.load(
                Map.of()
            );

        assertEquals(
            5,
            config.maxAttempts()
        );

        assertEquals(
            Duration.ofSeconds(
                30
            ),
            config.initialBackoff()
        );

        assertEquals(
            Duration.ofMinutes(
                5
            ),
            config.maxBackoff()
        );
    }

    @Test
    void shouldLoadExplicitConfiguration() {

        PublicationOutboxRetryConfig config =
            PublicationOutboxRetryConfigProvider.load(
                Map.of(
                    "PUBLICATION_OUTBOX_MAX_ATTEMPTS",
                    "7",
                    "PUBLICATION_OUTBOX_RETRY_INITIAL_BACKOFF",
                    "PT45S",
                    "PUBLICATION_OUTBOX_RETRY_MAX_BACKOFF",
                    "PT10M"
                )
            );

        assertEquals(
            7,
            config.maxAttempts()
        );

        assertEquals(
            Duration.ofSeconds(
                45
            ),
            config.initialBackoff()
        );

        assertEquals(
            Duration.ofMinutes(
                10
            ),
            config.maxBackoff()
        );
    }

    @Test
    void shouldRejectInvalidMaxAttempts() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationOutboxRetryConfigProvider.load(
                    Map.of(
                        "PUBLICATION_OUTBOX_MAX_ATTEMPTS",
                        "0"
                    )
                )
        );
    }

    @Test
    void shouldRejectInvalidDuration() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationOutboxRetryConfigProvider.load(
                    Map.of(
                        "PUBLICATION_OUTBOX_RETRY_INITIAL_BACKOFF",
                        "thirty-seconds"
                    )
                )
        );
    }

    @Test
    void shouldRejectMaximumBackoffShorterThanInitial() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationOutboxRetryConfigProvider.load(
                    Map.of(
                        "PUBLICATION_OUTBOX_RETRY_INITIAL_BACKOFF",
                        "PT5M",
                        "PUBLICATION_OUTBOX_RETRY_MAX_BACKOFF",
                        "PT30S"
                    )
                )
        );
    }
}
