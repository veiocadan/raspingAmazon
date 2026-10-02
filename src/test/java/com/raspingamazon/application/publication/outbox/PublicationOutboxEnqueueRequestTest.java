package com.raspingamazon.application.publication.outbox;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationOutboxEnqueueRequestTest {

    private static final OffsetDateTime AVAILABLE_AT =
        OffsetDateTime.parse(
            "2026-09-30T22:00:00-03:00"
        );

    private static final OffsetDateTime ENQUEUED_AT =
        OffsetDateTime.parse(
            "2026-09-30T21:00:00-03:00"
        );

    @Test
    void shouldCreateCadenceManagedRequest() {

        PublicationOutboxEnqueueRequest request =
            new PublicationOutboxEnqueueRequest(
                10L,
                20L,
                "  CADENCE_V4  ",
                AVAILABLE_AT,
                ENQUEUED_AT
            );

        assertEquals(
            10L,
            request.publicationId()
        );

        assertEquals(
            20L,
            request.selectionRunId()
        );

        assertEquals(
            "CADENCE_V4",
            request.cadenceProfileVersion()
        );

        assertEquals(
            AVAILABLE_AT,
            request.availableAt()
        );

        assertEquals(
            ENQUEUED_AT,
            request.enqueuedAt()
        );

        assertTrue(
            request.cadenceManaged()
        );
    }

    @Test
    void shouldKeepCompatibilityConstructorWithoutCadenceVersion() {

        PublicationOutboxEnqueueRequest request =
            new PublicationOutboxEnqueueRequest(
                10L,
                20L,
                AVAILABLE_AT,
                ENQUEUED_AT
            );

        assertEquals(
            null,
            request.cadenceProfileVersion()
        );

        assertFalse(
            request.cadenceManaged()
        );
    }

    @Test
    void shouldRejectBlankCadenceVersion() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxEnqueueRequest(
                    10L,
                    20L,
                    "   ",
                    AVAILABLE_AT,
                    ENQUEUED_AT
                )
        );
    }

    @Test
    void shouldRejectInvalidPublicationIdentity() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxEnqueueRequest(
                    0L,
                    20L,
                    "CADENCE_V1",
                    AVAILABLE_AT,
                    ENQUEUED_AT
                )
        );
    }

    @Test
    void shouldRejectInvalidSelectionRunIdentity() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxEnqueueRequest(
                    10L,
                    0L,
                    "CADENCE_V1",
                    AVAILABLE_AT,
                    ENQUEUED_AT
                )
        );
    }
}
