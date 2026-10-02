package com.raspingamazon.application.publication.outbox;

import com.raspingamazon.application.publication.channel.PublicationCommand;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationOutboxItemTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-27T20:00:00Z"
        );

    @Test
    void shouldExposeSelfContainedPublicationCommand() {

        PublicationOutboxItem item =
            processingItem();

        PublicationCommand command =
            item.command();

        assertEquals(
            10L,
            command.publicationId()
        );

        assertEquals(
            "TELEGRAM",
            command.channel()
        );

        assertEquals(
            "@phase18",
            command.destination()
        );

        assertEquals(
            "Oferta pronta",
            command.content()
        );

        assertTrue(
            item.processing()
        );

        assertFalse(
            item.finished()
        );
    }

    @Test
    void shouldRecognizePrimaryDeliveryWithQuotaReservation() {

        PublicationOutboxItem item =
            processingItem();

        assertTrue(
            item.reservesQuota()
        );

        assertFalse(
            item.derivedDelivery()
        );

        assertEquals(
            "PUBLICATION_QUOTA_V1",
            item.quotaProfileVersion()
        );

        assertEquals(
            LocalDate.of(
                2026,
                9,
                27
            ),
            item.quotaDate()
        );
    }

    @Test
    void shouldAllowDerivedDeliveryWithoutQuotaReservation() {

        PublicationOutboxItem item =
            new PublicationOutboxItem(
                2L,
                10L,
                20L,
                1,
                "WHATSAPP_MANUAL",
                "-1001234567890",
                "Oferta pronta",
                null,
                null,
                PublicationOutboxStatus.PENDING,
                NOW,
                null,
                null,
                NOW,
                NOW,
                null
            );

        assertFalse(
            item.reservesQuota()
        );

        assertTrue(
            item.derivedDelivery()
        );

        assertEquals(
            null,
            item.quotaProfileVersion()
        );

        assertEquals(
            null,
            item.quotaDate()
        );

        PublicationCommand command =
            item.command();

        assertEquals(
            "WHATSAPP_MANUAL",
            command.channel()
        );

        assertEquals(
            "-1001234567890",
            command.destination()
        );

        assertEquals(
            "Oferta pronta",
            command.content()
        );
    }

    @Test
    void shouldRejectPartialQuotaReservation() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxItem(
                    2L,
                    10L,
                    20L,
                    1,
                    "WHATSAPP_MANUAL",
                    "-1001234567890",
                    "Oferta pronta",
                    "PUBLICATION_QUOTA_V1",
                    null,
                    PublicationOutboxStatus.PENDING,
                    NOW,
                    null,
                    null,
                    NOW,
                    NOW,
                    null
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxItem(
                    2L,
                    10L,
                    20L,
                    1,
                    "WHATSAPP_MANUAL",
                    "-1001234567890",
                    "Oferta pronta",
                    null,
                    LocalDate.of(
                        2026,
                        9,
                        27
                    ),
                    PublicationOutboxStatus.PENDING,
                    NOW,
                    null,
                    null,
                    NOW,
                    NOW,
                    null
                )
        );
    }

    @Test
    void shouldRequireLockForProcessingItem() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationOutboxItem(
                    1L,
                    10L,
                    20L,
                    1,
                    "TELEGRAM",
                    "@phase18",
                    "Oferta",
                    "PUBLICATION_QUOTA_V1",
                    LocalDate.of(
                        2026,
                        9,
                        27
                    ),
                    PublicationOutboxStatus.PROCESSING,
                    NOW,
                    null,
                    "worker-a",
                    NOW,
                    NOW,
                    null
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationOutboxItem(
                    1L,
                    10L,
                    20L,
                    1,
                    "TELEGRAM",
                    "@phase18",
                    "Oferta",
                    "PUBLICATION_QUOTA_V1",
                    LocalDate.of(
                        2026,
                        9,
                        27
                    ),
                    PublicationOutboxStatus.PROCESSING,
                    NOW,
                    NOW,
                    null,
                    NOW,
                    NOW,
                    null
                )
        );
    }

    @Test
    void shouldRejectLockOutsideProcessingState() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxItem(
                    1L,
                    10L,
                    20L,
                    1,
                    "TELEGRAM",
                    "@phase18",
                    "Oferta",
                    "PUBLICATION_QUOTA_V1",
                    LocalDate.of(
                        2026,
                        9,
                        27
                    ),
                    PublicationOutboxStatus.PENDING,
                    NOW,
                    NOW,
                    "worker-a",
                    NOW,
                    NOW,
                    null
                )
        );
    }

    @Test
    void shouldRequireFinishedAtForTerminalState() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxItem(
                    1L,
                    10L,
                    20L,
                    1,
                    "TELEGRAM",
                    "@phase18",
                    "Oferta",
                    "PUBLICATION_QUOTA_V1",
                    LocalDate.of(
                        2026,
                        9,
                        27
                    ),
                    PublicationOutboxStatus.SUCCEEDED,
                    NOW,
                    null,
                    null,
                    NOW,
                    NOW,
                    null
                )
        );
    }

    @Test
    void shouldRecognizeTerminalFailure() {

        PublicationOutboxItem item =
            new PublicationOutboxItem(
                1L,
                10L,
                20L,
                1,
                "TELEGRAM",
                "@phase18",
                "Oferta",
                "PUBLICATION_QUOTA_V1",
                LocalDate.of(
                    2026,
                    9,
                    27
                ),
                PublicationOutboxStatus.FAILED_PERMANENT,
                NOW,
                null,
                null,
                NOW.minusMinutes(
                    5
                ),
                NOW,
                NOW
            );

        assertTrue(
            item.finished()
        );

        assertFalse(
            item.processing()
        );
    }

    private PublicationOutboxItem processingItem() {

        return new PublicationOutboxItem(
            1L,
            10L,
            20L,
            1,
            "TELEGRAM",
            "@phase18",
            "Oferta pronta",
            "PUBLICATION_QUOTA_V1",
            LocalDate.of(
                2026,
                9,
                27
            ),
            PublicationOutboxStatus.PROCESSING,
            NOW.minusMinutes(
                1
            ),
            NOW,
            "worker-a",
            NOW.minusHours(
                1
            ),
            NOW,
            null
        );
    }
}
