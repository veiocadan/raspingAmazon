package com.raspingamazon.application.publication.channel;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationResultRetryNotBeforeTest {

    private static final OffsetDateTime RETRY_NOT_BEFORE =
        OffsetDateTime.parse(
            "2026-10-06T22:00:00Z"
        );

    @Test
    void transientFailureShouldCarryProviderRetryFloor() {

        PublicationResult result =
            PublicationResult.failedTransientWithRetryNotBefore(
                "TELEGRAM_RATE_LIMITED",
                RETRY_NOT_BEFORE
            );

        assertEquals(
            PublicationResultStatus.FAILED_TRANSIENT,
            result.status()
        );

        assertEquals(
            RETRY_NOT_BEFORE,
            result.retryNotBeforeValue()
                .orElseThrow()
        );
    }

    @Test
    void compatibilityFactoriesShouldNotInventRetryFloor() {

        assertTrue(
            PublicationResult.failedTransient(
                    "TRANSIENT"
                )
                .retryNotBeforeValue()
                .isEmpty()
        );

        assertTrue(
            PublicationResult.failedTransient(
                    "TRANSIENT",
                    "provider-ref"
                )
                .retryNotBeforeValue()
                .isEmpty()
        );
    }

    @Test
    void transientFailureShouldPreserveProviderReferenceAndRetryFloor() {

        PublicationResult result =
            PublicationResult.failedTransient(
                "TRANSIENT",
                "provider-ref",
                RETRY_NOT_BEFORE
            );

        assertEquals(
            "provider-ref",
            result.providerReference()
        );

        assertEquals(
            RETRY_NOT_BEFORE,
            result.retryNotBefore()
        );
    }

    @Test
    void nonTransientResultsShouldRejectRetryFloor() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationResult(
                    PublicationResultStatus.DELIVERY_UNKNOWN,
                    null,
                    "UNKNOWN",
                    RETRY_NOT_BEFORE
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationResult(
                    PublicationResultStatus.FAILED_PERMANENT,
                    null,
                    "PERMANENT",
                    RETRY_NOT_BEFORE
                )
        );
    }

    @Test
    void retryFloorFactoryShouldRejectNullFloor() {

        assertThrows(
            NullPointerException.class,
            () ->
                PublicationResult.failedTransientWithRetryNotBefore(
                    "RATE_LIMITED",
                    null
                )
        );
    }
}
