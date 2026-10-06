package com.raspingamazon.application.orchestration.lease;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessingJobLeaseRecoveryDrainTest {

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-10-06T21:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldDrainFullBatchesUntilPartialBatch() {

        Queue<ProcessingJobLeaseRecoveryResult> pages =
            new ArrayDeque<>();

        pages.add(
            new ProcessingJobLeaseRecoveryResult(
                1,
                1
            )
        );

        pages.add(
            new ProcessingJobLeaseRecoveryResult(
                2,
                0
            )
        );

        pages.add(
            new ProcessingJobLeaseRecoveryResult(
                0,
                1
            )
        );

        AtomicInteger calls =
            new AtomicInteger();

        ProcessingJobLeaseRecoveryService service =
            new ProcessingJobLeaseRecoveryService(
                (
                    leaseExpiredBefore,
                    recoveredAt,
                    batchSize
                ) -> {

                    calls.incrementAndGet();

                    assertEquals(
                        2,
                        batchSize
                    );

                    return pages.remove();
                },
                Duration.ofMinutes(
                    15
                ),
                2,
                CLOCK
            );

        ProcessingJobLeaseRecoveryResult result =
            service.recoverUntilQuiescent();

        assertEquals(
            3,
            result.retryWaitCount()
        );

        assertEquals(
            2,
            result.deadCount()
        );

        assertEquals(
            3,
            calls.get()
        );
    }

    @Test
    void shouldStopAfterFirstPartialBatch() {

        AtomicInteger calls =
            new AtomicInteger();

        ProcessingJobLeaseRecoveryService service =
            new ProcessingJobLeaseRecoveryService(
                (
                    leaseExpiredBefore,
                    recoveredAt,
                    batchSize
                ) -> {

                    calls.incrementAndGet();

                    return new ProcessingJobLeaseRecoveryResult(
                        1,
                        0
                    );
                },
                Duration.ofMinutes(
                    15
                ),
                2,
                CLOCK
            );

        ProcessingJobLeaseRecoveryResult result =
            service.recoverUntilQuiescent();

        assertEquals(
            1,
            result.totalRecovered()
        );

        assertEquals(
            1,
            calls.get()
        );
    }

    @Test
    void shouldFailClosedWhenPortReturnsMoreThanRequestedBatch() {

        ProcessingJobLeaseRecoveryService service =
            new ProcessingJobLeaseRecoveryService(
                (
                    leaseExpiredBefore,
                    recoveredAt,
                    batchSize
                ) ->
                    new ProcessingJobLeaseRecoveryResult(
                        3,
                        0
                    ),
                Duration.ofMinutes(
                    15
                ),
                2,
                CLOCK
            );

        assertThrows(
            IllegalStateException.class,
            service::recoverUntilQuiescent
        );
    }
}
