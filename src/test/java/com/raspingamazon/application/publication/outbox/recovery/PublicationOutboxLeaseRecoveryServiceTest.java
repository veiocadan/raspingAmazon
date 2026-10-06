package com.raspingamazon.application.publication.outbox.recovery;

import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxQueuePort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationOutboxLeaseRecoveryServiceTest {

    private static final Instant NOW =
        Instant.parse(
            "2026-10-04T00:30:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

    @Test
    void shouldCalculateLeaseBoundaryAndRecoverOnce() {

        AtomicReference<OffsetDateTime> capturedLockedBefore =
            new AtomicReference<>();

        AtomicReference<OffsetDateTime> capturedRecoveredAt =
            new AtomicReference<>();

        PublicationOutboxQueuePort queue =
            queue(
                (
                    lockedBefore,
                    recoveredAt
                ) -> {

                    capturedLockedBefore.set(
                        lockedBefore
                    );

                    capturedRecoveredAt.set(
                        recoveredAt
                    );

                    return 3;
                }
            );

        PublicationOutboxLeaseRecoveryService service =
            new PublicationOutboxLeaseRecoveryService(
                queue,
                Duration.ofMinutes(
                    15
                ),
                CLOCK
            );

        PublicationOutboxLeaseRecoveryResult result =
            service.recoverOnce();

        assertEquals(
            3,
            result.recoveredLeaseCount()
        );

        assertEquals(
            OffsetDateTime.parse(
                "2026-10-04T00:15:00Z"
            ).toInstant(),
            capturedLockedBefore.get()
                .toInstant()
        );

        assertEquals(
            NOW,
            capturedRecoveredAt.get()
                .toInstant()
        );
    }

    @Test
    void shouldRejectNonPositiveLeaseDuration() {

        PublicationOutboxQueuePort queue =
            queue(
                (
                    lockedBefore,
                    recoveredAt
                ) -> 0
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxLeaseRecoveryService(
                    queue,
                    Duration.ZERO,
                    CLOCK
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationOutboxLeaseRecoveryService(
                    queue,
                    Duration.ofSeconds(
                        -1
                    ),
                    CLOCK
                )
        );
    }

    @Test
    void shouldRejectNegativeRecoveryCountReturnedByPort() {

        PublicationOutboxLeaseRecoveryService service =
            new PublicationOutboxLeaseRecoveryService(
                queue(
                    (
                        lockedBefore,
                        recoveredAt
                    ) -> -1
                ),
                Duration.ofMinutes(
                    15
                ),
                CLOCK
            );

        assertThrows(
            IllegalStateException.class,
            service::recoverOnce
        );
    }

    private PublicationOutboxQueuePort queue(
        Recovery recovery
    ) {

        return new PublicationOutboxQueuePort() {

            @Override
            public Optional<PublicationOutboxItem> claimNext(
                String workerId,
                OffsetDateTime claimedAt
            ) {

                throw new UnsupportedOperationException();
            }

            @Override
            public int recoverExpiredLeases(
                OffsetDateTime lockedBefore,
                OffsetDateTime recoveredAt
            ) {

                return recovery.recover(
                    lockedBefore,
                    recoveredAt
                );
            }
        };
    }

    @FunctionalInterface
    private interface Recovery {

        int recover(
            OffsetDateTime lockedBefore,
            OffsetDateTime recoveredAt
        );
    }
}
