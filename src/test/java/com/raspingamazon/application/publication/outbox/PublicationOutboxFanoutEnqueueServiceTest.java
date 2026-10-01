package com.raspingamazon.application.publication.outbox;

import com.raspingamazon.application.publication.outbox.port.PublicationOutboxDerivedEnqueuePort;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxEnqueuePort;
import com.raspingamazon.application.shared.port.TransactionPort;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationOutboxFanoutEnqueueServiceTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-30T20:30:00Z"
        );

    private static final PublicationOutboxDerivedTarget
        WHATSAPP_MANUAL_TARGET =
        new PublicationOutboxDerivedTarget(
            "WHATSAPP_MANUAL",
            "-1001234567890"
        );

    @Test
    void shouldCreateDerivedDeliveryAfterPrimaryEnqueue() {

        PublicationOutboxEnqueuePort primary =
            request ->
                PublicationOutboxEnqueueResult.enqueued(
                    100L
                );

        List<PublicationOutboxDerivedEnqueueRequest>
            derivedRequests =
            new ArrayList<>();

        PublicationOutboxDerivedEnqueuePort derived =
            request -> {

                derivedRequests.add(
                    request
                );

                return PublicationOutboxEnqueueResult.enqueued(
                    200L
                );
            };

        PublicationOutboxFanoutEnqueueService service =
            service(
                primary,
                derived,
                List.of(
                    WHATSAPP_MANUAL_TARGET
                )
            );

        PublicationOutboxEnqueueResult result =
            service.enqueue(
                request()
            );

        assertTrue(
            result.enqueued()
        );

        assertEquals(
            100L,
            result.outboxIdValue()
                .orElseThrow()
        );

        assertEquals(
            1,
            derivedRequests.size()
        );

        PublicationOutboxDerivedEnqueueRequest derivedRequest =
            derivedRequests.getFirst();

        assertEquals(
            100L,
            derivedRequest.sourceOutboxId()
        );

        assertEquals(
            "WHATSAPP_MANUAL",
            derivedRequest.channel()
        );

        assertEquals(
            "-1001234567890",
            derivedRequest.destination()
        );

        assertEquals(
            NOW,
            derivedRequest.enqueuedAt()
        );
    }

    @Test
    void shouldEnsureDerivedDeliveryWhenPrimaryWasAlreadyEnqueued() {

        PublicationOutboxEnqueuePort primary =
            request ->
                PublicationOutboxEnqueueResult.alreadyEnqueued(
                    100L
                );

        AtomicInteger derivedCalls =
            new AtomicInteger();

        PublicationOutboxDerivedEnqueuePort derived =
            request -> {

                derivedCalls.incrementAndGet();

                return PublicationOutboxEnqueueResult
                    .alreadyEnqueued(
                        200L
                    );
            };

        PublicationOutboxFanoutEnqueueService service =
            service(
                primary,
                derived,
                List.of(
                    WHATSAPP_MANUAL_TARGET
                )
            );

        PublicationOutboxEnqueueResult result =
            service.enqueue(
                request()
            );

        assertTrue(
            result.alreadyEnqueued()
        );

        assertEquals(
            1,
            derivedCalls.get()
        );
    }

    @Test
    void shouldNotCreateDerivedDeliveryWhenPrimaryQuotaIsExhausted() {

        PublicationOutboxEnqueuePort primary =
            request ->
                PublicationOutboxEnqueueResult
                    .quotaExhaustedResult();

        AtomicInteger derivedCalls =
            new AtomicInteger();

        PublicationOutboxDerivedEnqueuePort derived =
            request -> {

                derivedCalls.incrementAndGet();

                return PublicationOutboxEnqueueResult.enqueued(
                    200L
                );
            };

        PublicationOutboxFanoutEnqueueService service =
            service(
                primary,
                derived,
                List.of(
                    WHATSAPP_MANUAL_TARGET
                )
            );

        PublicationOutboxEnqueueResult result =
            service.enqueue(
                request()
            );

        assertTrue(
            result.quotaExhausted()
        );

        assertEquals(
            0,
            derivedCalls.get()
        );
    }

    @Test
    void shouldNotCreateDerivedDeliveryWhenPrimarySelectionIsStale() {

        PublicationOutboxEnqueuePort primary =
            request ->
                PublicationOutboxEnqueueResult
                    .staleSelectionResult();

        AtomicInteger derivedCalls =
            new AtomicInteger();

        PublicationOutboxDerivedEnqueuePort derived =
            request -> {

                derivedCalls.incrementAndGet();

                return PublicationOutboxEnqueueResult.enqueued(
                    200L
                );
            };

        PublicationOutboxFanoutEnqueueService service =
            service(
                primary,
                derived,
                List.of(
                    WHATSAPP_MANUAL_TARGET
                )
            );

        PublicationOutboxEnqueueResult result =
            service.enqueue(
                request()
            );

        assertTrue(
            result.staleSelection()
        );

        assertEquals(
            0,
            derivedCalls.get()
        );
    }

    @Test
    void shouldCreateAllConfiguredDerivedTargets() {

        PublicationOutboxEnqueuePort primary =
            request ->
                PublicationOutboxEnqueueResult.enqueued(
                    100L
                );

        List<PublicationOutboxDerivedEnqueueRequest>
            derivedRequests =
            new ArrayList<>();

        PublicationOutboxDerivedEnqueuePort derived =
            request -> {

                derivedRequests.add(
                    request
                );

                return PublicationOutboxEnqueueResult.enqueued(
                    200L
                        + derivedRequests.size()
                );
            };

        PublicationOutboxFanoutEnqueueService service =
            service(
                primary,
                derived,
                List.of(
                    new PublicationOutboxDerivedTarget(
                        "WHATSAPP_MANUAL",
                        "-1001111111111"
                    ),
                    new PublicationOutboxDerivedTarget(
                        "AUDIT_MIRROR",
                        "-1002222222222"
                    )
                )
            );

        service.enqueue(
            request()
        );

        assertEquals(
            2,
            derivedRequests.size()
        );

        assertEquals(
            "WHATSAPP_MANUAL",
            derivedRequests.get(
                0
            ).channel()
        );

        assertEquals(
            "AUDIT_MIRROR",
            derivedRequests.get(
                1
            ).channel()
        );
    }

    @Test
    void shouldAllowNoDerivedTargets() {

        PublicationOutboxEnqueueResult expected =
            PublicationOutboxEnqueueResult.enqueued(
                100L
            );

        PublicationOutboxEnqueuePort primary =
            request ->
                expected;

        AtomicInteger derivedCalls =
            new AtomicInteger();

        PublicationOutboxDerivedEnqueuePort derived =
            request -> {

                derivedCalls.incrementAndGet();

                return PublicationOutboxEnqueueResult.enqueued(
                    200L
                );
            };

        PublicationOutboxFanoutEnqueueService service =
            service(
                primary,
                derived,
                List.of()
            );

        PublicationOutboxEnqueueResult actual =
            service.enqueue(
                request()
            );

        assertSame(
            expected,
            actual
        );

        assertEquals(
            0,
            derivedCalls.get()
        );
    }

    @Test
    void shouldRejectDuplicateDerivedTargets() {

        PublicationOutboxEnqueuePort primary =
            request ->
                PublicationOutboxEnqueueResult.enqueued(
                    100L
                );

        PublicationOutboxDerivedEnqueuePort derived =
            request ->
                PublicationOutboxEnqueueResult.enqueued(
                    200L
                );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service(
                    primary,
                    derived,
                    List.of(
                        WHATSAPP_MANUAL_TARGET,
                        new PublicationOutboxDerivedTarget(
                            "WHATSAPP_MANUAL",
                            "-1001234567890"
                        )
                    )
                )
        );
    }

    @Test
    void shouldRejectUnsupportedDerivedResult() {

        PublicationOutboxEnqueuePort primary =
            request ->
                PublicationOutboxEnqueueResult.enqueued(
                    100L
                );

        PublicationOutboxDerivedEnqueuePort derived =
            request ->
                PublicationOutboxEnqueueResult
                    .quotaExhaustedResult();

        PublicationOutboxFanoutEnqueueService service =
            service(
                primary,
                derived,
                List.of(
                    WHATSAPP_MANUAL_TARGET
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.enqueue(
                    request()
                )
        );
    }

    @Test
    void shouldPropagateDerivedFailureInsideTransactionBoundary() {

        AtomicInteger transactionExecutions =
            new AtomicInteger();

        TransactionPort transactionPort =
            new RecordingTransactionPort(
                transactionExecutions
            );

        PublicationOutboxEnqueuePort primary =
            request ->
                PublicationOutboxEnqueueResult.enqueued(
                    100L
                );

        RuntimeException expected =
            new RuntimeException(
                "derived failed"
            );

        PublicationOutboxDerivedEnqueuePort derived =
            request -> {
                throw expected;
            };

        PublicationOutboxFanoutEnqueueService service =
            new PublicationOutboxFanoutEnqueueService(
                primary,
                derived,
                transactionPort,
                List.of(
                    WHATSAPP_MANUAL_TARGET
                )
            );

        RuntimeException actual =
            assertThrows(
                RuntimeException.class,
                () ->
                    service.enqueue(
                        request()
                    )
            );

        assertSame(
            expected,
            actual
        );

        assertEquals(
            1,
            transactionExecutions.get()
        );
    }

    private PublicationOutboxFanoutEnqueueService service(
        PublicationOutboxEnqueuePort primary,
        PublicationOutboxDerivedEnqueuePort derived,
        List<PublicationOutboxDerivedTarget> targets
    ) {

        return new PublicationOutboxFanoutEnqueueService(
            primary,
            derived,
            new ImmediateTransactionPort(),
            targets
        );
    }

    private PublicationOutboxEnqueueRequest request() {

        return new PublicationOutboxEnqueueRequest(
            10L,
            20L,
            NOW.plusMinutes(
                5
            ),
            NOW
        );
    }

    private static final class ImmediateTransactionPort
        implements TransactionPort {

        @Override
        public <T> T execute(
            Supplier<T> operation
        ) {

            return operation.get();
        }
    }

    private static final class RecordingTransactionPort
        implements TransactionPort {

        private final AtomicInteger executionCounter;

        private RecordingTransactionPort(
            AtomicInteger executionCounter
        ) {

            this.executionCounter =
                executionCounter;
        }

        @Override
        public <T> T execute(
            Supplier<T> operation
        ) {

            executionCounter.incrementAndGet();

            return operation.get();
        }
    }
}
