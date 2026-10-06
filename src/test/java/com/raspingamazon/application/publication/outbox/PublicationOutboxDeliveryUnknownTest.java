package com.raspingamazon.application.publication.outbox;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationOutboxDeliveryUnknownTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-03T20:00:00Z"
        );

    @Test
    void shouldTreatDeliveryUnknownAsTerminal() {

        PublicationOutboxItem item =
            deliveryUnknownItem(
                NOW
            );

        assertTrue(
            item.finished()
        );

        assertFalse(
            item.processing()
        );
    }

    @Test
    void shouldRequireFinishedAtForDeliveryUnknown() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                deliveryUnknownItem(
                    null
                )
        );
    }

    private PublicationOutboxItem deliveryUnknownItem(
        OffsetDateTime finishedAt
    ) {

        return new PublicationOutboxItem(
            1L,
            10L,
            20L,
            1,
            "TELEGRAM",
            "@phase20",
            "Oferta pronta",
            "PUBLICATION_QUOTA_V1",
            LocalDate.of(
                2026,
                10,
                3
            ),
            PublicationOutboxStatus.DELIVERY_UNKNOWN,
            NOW.minusMinutes(
                10
            ),
            null,
            null,
            NOW.minusHours(
                1
            ),
            NOW,
            finishedAt
        );
    }
}
