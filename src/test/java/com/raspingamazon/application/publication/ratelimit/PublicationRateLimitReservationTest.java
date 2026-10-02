package com.raspingamazon.application.publication.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationRateLimitReservationTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-01T23:00:00Z"
        );

    @Test
    void immediateAdmissionShouldAdvanceNextAllowedTime() {

        PublicationRateLimitReservation reservation =
            new PublicationRateLimitReservation(
                "TELEGRAM_BOT_API",
                NOW,
                NOW,
                NOW.plusSeconds(
                    1
                )
            );

        assertTrue(
            reservation.immediatelyAllowed()
        );

        assertFalse(
            reservation.deferred()
        );
    }

    @Test
    void deferredAdmissionShouldNotReserveAnotherFutureSlot() {

        OffsetDateTime retryAt =
            NOW.plusSeconds(
                1
            );

        PublicationRateLimitReservation reservation =
            new PublicationRateLimitReservation(
                "TELEGRAM_BOT_API",
                NOW,
                retryAt,
                retryAt
            );

        assertFalse(
            reservation.immediatelyAllowed()
        );

        assertTrue(
            reservation.deferred()
        );
    }

    @Test
    void shouldRejectAllowedTimeBeforeRequestedTime() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationRateLimitReservation(
                    "TELEGRAM_BOT_API",
                    NOW,
                    NOW.minusSeconds(
                        1
                    ),
                    NOW
                )
        );
    }

    @Test
    void immediateAdmissionShouldRequireStateAdvance() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationRateLimitReservation(
                    "TELEGRAM_BOT_API",
                    NOW,
                    NOW,
                    NOW
                )
        );
    }

    @Test
    void deferredAdmissionShouldNotAdvanceBeyondRetryInstant() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationRateLimitReservation(
                    "TELEGRAM_BOT_API",
                    NOW,
                    NOW.plusSeconds(
                        1
                    ),
                    NOW.plusSeconds(
                        2
                    )
                )
        );
    }

    @Test
    void shouldRejectNextAllowedTimeBeforeAllowedTime() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationRateLimitReservation(
                    "TELEGRAM_BOT_API",
                    NOW,
                    NOW.plusSeconds(
                        2
                    ),
                    NOW.plusSeconds(
                        1
                    )
                )
        );
    }
}
