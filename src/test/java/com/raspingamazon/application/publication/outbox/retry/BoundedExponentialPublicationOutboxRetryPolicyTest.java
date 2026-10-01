package com.raspingamazon.application.publication.outbox.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedExponentialPublicationOutboxRetryPolicyTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-01T20:00:00-03:00"
        );

    @Test
    void firstFailureShouldUseInitialBackoff() {

        BoundedExponentialPublicationOutboxRetryPolicy policy =
            new BoundedExponentialPublicationOutboxRetryPolicy(
                5,
                Duration.ofSeconds(
                    30
                ),
                Duration.ofMinutes(
                    5
                )
            );

        assertEquals(
            Optional.of(
                NOW.plusSeconds(
                    30
                )
            ),
            policy.nextAttemptAt(
                1,
                NOW
            )
        );
    }

    @Test
    void backoffShouldDoublePerCompletedAttempt() {

        BoundedExponentialPublicationOutboxRetryPolicy policy =
            new BoundedExponentialPublicationOutboxRetryPolicy(
                6,
                Duration.ofSeconds(
                    30
                ),
                Duration.ofMinutes(
                    10
                )
            );

        assertEquals(
            Optional.of(
                NOW.plusSeconds(
                    60
                )
            ),
            policy.nextAttemptAt(
                2,
                NOW
            )
        );

        assertEquals(
            Optional.of(
                NOW.plusSeconds(
                    120
                )
            ),
            policy.nextAttemptAt(
                3,
                NOW
            )
        );
    }

    @Test
    void backoffShouldBeLimitedByMaximum() {

        BoundedExponentialPublicationOutboxRetryPolicy policy =
            new BoundedExponentialPublicationOutboxRetryPolicy(
                10,
                Duration.ofMinutes(
                    2
                ),
                Duration.ofMinutes(
                    5
                )
            );

        assertEquals(
            Optional.of(
                NOW.plusMinutes(
                    5
                )
            ),
            policy.nextAttemptAt(
                4,
                NOW
            )
        );
    }

    @Test
    void lastAllowedAttemptShouldNotScheduleAnotherRetry() {

        BoundedExponentialPublicationOutboxRetryPolicy policy =
            new BoundedExponentialPublicationOutboxRetryPolicy(
                5,
                Duration.ofSeconds(
                    30
                ),
                Duration.ofMinutes(
                    5
                )
            );

        assertTrue(
            policy.nextAttemptAt(
                5,
                NOW
            ).isEmpty()
        );
    }

    @Test
    void attemptBeyondLimitShouldAlsoRemainTerminal() {

        BoundedExponentialPublicationOutboxRetryPolicy policy =
            new BoundedExponentialPublicationOutboxRetryPolicy(
                5,
                Duration.ofSeconds(
                    30
                ),
                Duration.ofMinutes(
                    5
                )
            );

        assertTrue(
            policy.nextAttemptAt(
                6,
                NOW
            ).isEmpty()
        );
    }

    @Test
    void shouldRejectInvalidMaxAttempts() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BoundedExponentialPublicationOutboxRetryPolicy(
                    0,
                    Duration.ofSeconds(
                        30
                    ),
                    Duration.ofMinutes(
                        5
                    )
                )
        );
    }

    @Test
    void shouldRejectZeroInitialBackoff() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BoundedExponentialPublicationOutboxRetryPolicy(
                    5,
                    Duration.ZERO,
                    Duration.ofMinutes(
                        5
                    )
                )
        );
    }

    @Test
    void shouldRejectMaximumShorterThanInitialBackoff() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BoundedExponentialPublicationOutboxRetryPolicy(
                    5,
                    Duration.ofMinutes(
                        5
                    ),
                    Duration.ofMinutes(
                        1
                    )
                )
        );
    }

    @Test
    void shouldRejectInvalidCompletedAttemptNumber() {

        BoundedExponentialPublicationOutboxRetryPolicy policy =
            new BoundedExponentialPublicationOutboxRetryPolicy(
                5,
                Duration.ofSeconds(
                    30
                ),
                Duration.ofMinutes(
                    5
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                policy.nextAttemptAt(
                    0,
                    NOW
                )
        );
    }
}
