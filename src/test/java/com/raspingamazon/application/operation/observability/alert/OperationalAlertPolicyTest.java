package com.raspingamazon.application.operation.observability.alert;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationalAlertPolicyTest {

    @Test
    void shouldAcceptExplicitValidPolicy() {

        OperationalAlertPolicy policy =
            validPolicy();

        assertEquals(
            3,
            policy.repeatedExternalFailureThreshold()
        );

        assertEquals(
            Duration.ofMinutes(
                15
            ),
            policy.repeatedExternalFailureWindow()
        );

        assertEquals(
            5,
            policy.suspiciousCollectionLookbackRuns()
        );

        assertEquals(
            new BigDecimal(
                "0.50"
            ),
            policy.suspiciousCollectionDropFraction()
        );

        assertEquals(
            20L,
            policy.suspiciousCollectionMinimumBaselineCandidates()
        );
    }

    @Test
    void shouldRejectInvalidRepeatedExternalFailureThreshold() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlertPolicy(
                1,
                Duration.ofMinutes(
                    15
                ),
                5,
                new BigDecimal(
                    "0.50"
                ),
                20L
            )
        );
    }

    @Test
    void shouldRejectNonPositiveExternalFailureWindow() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlertPolicy(
                3,
                Duration.ZERO,
                5,
                new BigDecimal(
                    "0.50"
                ),
                20L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlertPolicy(
                3,
                Duration.ofMinutes(
                    -1
                ),
                5,
                new BigDecimal(
                    "0.50"
                ),
                20L
            )
        );
    }

    @Test
    void shouldRejectInvalidSuspiciousCollectionLookback() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlertPolicy(
                3,
                Duration.ofMinutes(
                    15
                ),
                0,
                new BigDecimal(
                    "0.50"
                ),
                20L
            )
        );
    }

    @Test
    void shouldRejectInvalidSuspiciousCollectionThresholds() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlertPolicy(
                3,
                Duration.ofMinutes(
                    15
                ),
                5,
                BigDecimal.ZERO,
                20L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlertPolicy(
                3,
                Duration.ofMinutes(
                    15
                ),
                5,
                BigDecimal.ONE,
                20L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlertPolicy(
                3,
                Duration.ofMinutes(
                    15
                ),
                5,
                new BigDecimal(
                    "0.50"
                ),
                0L
            )
        );
    }

    private OperationalAlertPolicy validPolicy() {

        return new OperationalAlertPolicy(
            3,
            Duration.ofMinutes(
                15
            ),
            5,
            new BigDecimal(
                "0.50"
            ),
            20L
        );
    }
}
