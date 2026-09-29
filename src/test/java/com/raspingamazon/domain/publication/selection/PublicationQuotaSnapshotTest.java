package com.raspingamazon.domain.publication.selection;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationQuotaSnapshotTest {

    private static final LocalDate QUOTA_DATE =
        LocalDate.of(
            2026,
            9,
            27
        );

    @Test
    void shouldCalculateAvailableSlots() {

        PublicationQuotaSnapshot snapshot =
            snapshot(
                7,
                3L
            );

        assertEquals(
            4,
            snapshot.availableSlots()
        );

        assertTrue(
            snapshot.hasAvailableSlots()
        );

        assertFalse(
            snapshot.exhausted()
        );
    }

    @Test
    void shouldBeExhaustedWhenQuotaIsFullyOccupied() {

        PublicationQuotaSnapshot snapshot =
            snapshot(
                7,
                7L
            );

        assertEquals(
            0,
            snapshot.availableSlots()
        );

        assertFalse(
            snapshot.hasAvailableSlots()
        );

        assertTrue(
            snapshot.exhausted()
        );
    }

    @Test
    void shouldRemainExhaustedWhenOccupiedSlotsExceedCurrentLimit() {

        PublicationQuotaSnapshot snapshot =
            snapshot(
                5,
                8L
            );

        assertEquals(
            0,
            snapshot.availableSlots()
        );

        assertTrue(
            snapshot.exhausted()
        );
    }

    @Test
    void shouldExposeCompleteQuotaContext() {

        PublicationQuotaSnapshot snapshot =
            snapshot(
                7,
                2L
            );

        assertEquals(
            "TELEGRAM",
            snapshot.channel()
        );

        assertEquals(
            "@phase18",
            snapshot.destination()
        );

        assertEquals(
            QUOTA_DATE,
            snapshot.quotaDate()
        );

        assertEquals(
            "PUBLICATION_QUOTA_V1",
            snapshot.quotaProfileVersion()
        );

        assertEquals(
            7,
            snapshot.maxPublicationsPerDay()
        );

        assertEquals(
            2L,
            snapshot.occupiedSlots()
        );
    }

    @Test
    void shouldRejectNegativeOccupiedSlots() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                snapshot(
                    7,
                    -1L
                )
        );
    }

    @Test
    void shouldRejectNonPositiveMaximum() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                snapshot(
                    0,
                    0L
                )
        );
    }

    @Test
    void shouldRejectBlankChannel() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationQuotaSnapshot(
                    "   ",
                    "@phase18",
                    QUOTA_DATE,
                    "PUBLICATION_QUOTA_V1",
                    7,
                    0L
                )
        );
    }

    @Test
    void shouldRejectBlankDestination() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationQuotaSnapshot(
                    "TELEGRAM",
                    "",
                    QUOTA_DATE,
                    "PUBLICATION_QUOTA_V1",
                    7,
                    0L
                )
        );
    }

    private PublicationQuotaSnapshot snapshot(
        int maxPublicationsPerDay,
        long occupiedSlots
    ) {

        return new PublicationQuotaSnapshot(
            "TELEGRAM",
            "@phase18",
            QUOTA_DATE,
            "PUBLICATION_QUOTA_V1",
            maxPublicationsPerDay,
            occupiedSlots
        );
    }
}
