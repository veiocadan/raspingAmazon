package com.raspingamazon.application.orchestration.failure;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExponentialRetryBackoffPolicyTest {

    @Test
    void shouldIncreaseDelayExponentially() {

        ExponentialRetryBackoffPolicy policy =
            new ExponentialRetryBackoffPolicy(
                Duration.ofSeconds(
                    30
                ),
                Duration.ofMinutes(
                    10
                )
            );

        assertEquals(
            Duration.ofSeconds(
                30
            ),
            policy.delayForAttempt(
                1
            )
        );

        assertEquals(
            Duration.ofSeconds(
                60
            ),
            policy.delayForAttempt(
                2
            )
        );

        assertEquals(
            Duration.ofSeconds(
                120
            ),
            policy.delayForAttempt(
                3
            )
        );

        assertEquals(
            Duration.ofSeconds(
                240
            ),
            policy.delayForAttempt(
                4
            )
        );
    }

    @Test
    void shouldRespectMaximumDelay() {

        ExponentialRetryBackoffPolicy policy =
            new ExponentialRetryBackoffPolicy(
                Duration.ofSeconds(
                    30
                ),
                Duration.ofMinutes(
                    5
                )
            );

        assertEquals(
            Duration.ofMinutes(
                5
            ),
            policy.delayForAttempt(
                10
            )
        );
    }

    @Test
    void shouldRejectInvalidAttemptCount() {

        ExponentialRetryBackoffPolicy policy =
            new ExponentialRetryBackoffPolicy(
                Duration.ofSeconds(
                    30
                ),
                Duration.ofMinutes(
                    5
                )
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> policy.delayForAttempt(
                0
            )
        );
    }

    @Test
    void shouldRejectInvalidConfiguration() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ExponentialRetryBackoffPolicy(
                    Duration.ZERO,
                    Duration.ofMinutes(
                        5
                    )
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ExponentialRetryBackoffPolicy(
                    Duration.ofMinutes(
                        10
                    ),
                    Duration.ofMinutes(
                        5
                    )
                )
        );
    }
}
