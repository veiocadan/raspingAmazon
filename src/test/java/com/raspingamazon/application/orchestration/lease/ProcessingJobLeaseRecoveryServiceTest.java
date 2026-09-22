package com.raspingamazon.application.orchestration.lease;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingJobLeaseRecoveryServiceTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-23T00:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-23T00:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldCalculateLeaseExpirationAndDelegateRecovery() {

        RecordingRecoveryPort port =
            new RecordingRecoveryPort();

        port.result =
            new ProcessingJobLeaseRecoveryResult(
                2,
                1
            );

        ProcessingJobLeaseRecoveryService service =
            new ProcessingJobLeaseRecoveryService(
                port,
                Duration.ofMinutes(
                    5
                ),
                100,
                CLOCK
            );

        ProcessingJobLeaseRecoveryResult result =
            service.recoverOnce();

        assertEquals(
            1,
            port.calls
        );

        assertEquals(
            NOW.minusMinutes(
                5
            ),
            port.lastLeaseExpiredBefore
        );

        assertEquals(
            NOW,
            port.lastRecoveredAt
        );

        assertEquals(
            100,
            port.lastBatchSize
        );

        assertEquals(
            2,
            result.retryWaitCount()
        );

        assertEquals(
            1,
            result.deadCount()
        );

        assertEquals(
            3,
            result.totalRecovered()
        );
    }

    @Test
    void shouldReturnEmptyRecoveryResult() {

        RecordingRecoveryPort port =
            new RecordingRecoveryPort();

        port.result =
            new ProcessingJobLeaseRecoveryResult(
                0,
                0
            );

        ProcessingJobLeaseRecoveryService service =
            new ProcessingJobLeaseRecoveryService(
                port,
                Duration.ofMinutes(
                    10
                ),
                50,
                CLOCK
            );

        ProcessingJobLeaseRecoveryResult result =
            service.recoverOnce();

        assertEquals(
            0,
            result.totalRecovered()
        );

        assertEquals(
            NOW.minusMinutes(
                10
            ),
            port.lastLeaseExpiredBefore
        );
    }

    @Test
    void shouldRejectInvalidLeaseDuration() {

        RecordingRecoveryPort port =
            new RecordingRecoveryPort();

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ProcessingJobLeaseRecoveryService(
                    port,
                    Duration.ZERO,
                    100,
                    CLOCK
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ProcessingJobLeaseRecoveryService(
                    port,
                    Duration.ofSeconds(
                        -1
                    ),
                    100,
                    CLOCK
                )
        );
    }

    @Test
    void shouldRejectInvalidBatchSize() {

        RecordingRecoveryPort port =
            new RecordingRecoveryPort();

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ProcessingJobLeaseRecoveryService(
                    port,
                    Duration.ofMinutes(
                        5
                    ),
                    0,
                    CLOCK
                )
        );
    }

    private static final class RecordingRecoveryPort
        implements ProcessingJobLeaseRecoveryPort {

        private int calls;

        private OffsetDateTime
            lastLeaseExpiredBefore;

        private OffsetDateTime
            lastRecoveredAt;

        private int lastBatchSize;

        private ProcessingJobLeaseRecoveryResult result =
            new ProcessingJobLeaseRecoveryResult(
                0,
                0
            );

        @Override
        public ProcessingJobLeaseRecoveryResult recoverExpiredLeases(
            OffsetDateTime leaseExpiredBefore,
            OffsetDateTime recoveredAt,
            int batchSize
        ) {

            calls++;

            lastLeaseExpiredBefore =
                leaseExpiredBefore;

            lastRecoveredAt =
                recoveredAt;

            lastBatchSize =
                batchSize;

            return result;
        }
    }
}
