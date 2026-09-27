package com.raspingamazon.application.operation.observability.alert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationalAlertTest {

    @Test
    void shouldRepresentRepeatedExternalFailures() {

        OperationalAlert alert =
            new OperationalAlert(
                OperationalAlertType.REPEATED_EXTERNAL_FAILURES,
                null,
                "amazon-product-page",
                4L,
                null
            );

        assertEquals(
            OperationalAlertType.REPEATED_EXTERNAL_FAILURES,
            alert.type()
        );

        assertEquals(
            "amazon-product-page",
            alert.integration()
        );

        assertEquals(
            4L,
            alert.observedCount()
        );

        assertNull(
            alert.runId()
        );
    }

    @Test
    void shouldRepresentDeadJobs() {

        OperationalAlert alert =
            new OperationalAlert(
                OperationalAlertType.DEAD_JOBS,
                101L,
                null,
                2L,
                null
            );

        assertEquals(
            101L,
            alert.runId()
        );

        assertEquals(
            2L,
            alert.observedCount()
        );
    }

    @Test
    void shouldRepresentZeroCandidates() {

        OperationalAlert alert =
            new OperationalAlert(
                OperationalAlertType.ZERO_CANDIDATES,
                202L,
                null,
                0L,
                null
            );

        assertEquals(
            0L,
            alert.observedCount()
        );
    }

    @Test
    void shouldRepresentSuspiciousCollectionDrop() {

        OperationalAlert alert =
            new OperationalAlert(
                OperationalAlertType.SUSPICIOUS_COLLECTION_DROP,
                303L,
                null,
                12L,
                80L
            );

        assertEquals(
            12L,
            alert.observedCount()
        );

        assertEquals(
            80L,
            alert.referenceCount()
        );
    }

    @Test
    void shouldRejectInvalidTypeSpecificEvidence() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlert(
                OperationalAlertType.REPEATED_EXTERNAL_FAILURES,
                null,
                null,
                3L,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlert(
                OperationalAlertType.DEAD_JOBS,
                null,
                null,
                1L,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlert(
                OperationalAlertType.ZERO_CANDIDATES,
                10L,
                null,
                1L,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlert(
                OperationalAlertType.SUSPICIOUS_COLLECTION_DROP,
                10L,
                null,
                100L,
                50L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalAlert(
                OperationalAlertType.SUSPICIOUS_COLLECTION_DROP,
                10L,
                null,
                10L,
                null
            )
        );
    }
}
