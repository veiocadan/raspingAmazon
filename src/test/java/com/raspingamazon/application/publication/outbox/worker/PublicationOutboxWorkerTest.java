package com.raspingamazon.application.publication.outbox.worker;

import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationChannelResolver;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.channel.PublicationResultStatus;
import com.raspingamazon.application.publication.outbox.PublicationAttemptHandle;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.port.PublicationAttemptCompletionPort;
import com.raspingamazon.application.publication.outbox.port.PublicationAttemptStartPort;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxQueuePort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationOutboxWorkerTest {

    private static final String WORKER_ID =
        "publication-worker-01";

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-27T20:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-09-27T20:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldReturnIdleWithoutStartingAttempt() {

        RecordingQueue queue =
            new RecordingQueue();

        PublicationChannelResolver resolver =
            channel -> {
                throw new AssertionError(
                    "resolver must not be called"
                );
            };

        PublicationAttemptStartPort startPort =
            (
                outboxId,
                workerId,
                startedAt
            ) -> {
                throw new AssertionError(
                    "start must not be called"
                );
            };

        PublicationAttemptCompletionPort completionPort =
            (
                attempt,
                workerId,
                result,
                completedAt
            ) -> {
                throw new AssertionError(
                    "completion must not be called"
                );
            };

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                resolver,
                startPort,
                completionPort,
                CLOCK
            );

        PublicationOutboxWorkerRunResult result =
            worker.runOnce();

        assertFalse(
            result.outboxClaimed()
        );

        assertEquals(
            null,
            result.claimedOutboxId()
        );

        assertEquals(
            null,
            result.finalStatus()
        );

        assertEquals(
            1,
            queue.claimCalls
        );

        assertEquals(
            WORKER_ID,
            queue.lastWorkerId
        );

        assertEquals(
            NOW,
            queue.lastClaimedAt
        );
    }

    @Test
    void shouldStartBeforeProviderAndCompleteSameAttemptAfterProvider() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        List<String> sequence =
            new ArrayList<>();

        RecordingStart start =
            new RecordingStart(
                claimed,
                sequence
            );

        PublicationResult configuredResult =
            PublicationResult.success(
                "fake-provider-reference"
            );

        AtomicInteger providerCalls =
            new AtomicInteger();

        PublicationChannel channel =
            command -> {

                sequence.add(
                    "provider"
                );

                assertEquals(
                    1,
                    start.calls
                );

                providerCalls.incrementAndGet();

                assertEquals(
                    claimed.publicationId(),
                    command.publicationId()
                );

                assertEquals(
                    claimed.channel(),
                    command.channel()
                );

                assertEquals(
                    claimed.destination(),
                    command.destination()
                );

                assertEquals(
                    claimed.content(),
                    command.content()
                );

                return configuredResult;
            };

        RecordingCompletion completion =
            new RecordingCompletion(
                claimed,
                sequence
            );

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                logicalChannel ->
                    channel,
                start,
                completion,
                CLOCK
            );

        PublicationOutboxWorkerRunResult result =
            worker.runOnce();

        assertTrue(
            result.outboxClaimed()
        );

        assertEquals(
            claimed.id(),
            result.claimedOutboxId()
        );

        assertEquals(
            PublicationOutboxStatus.SUCCEEDED,
            result.finalStatus()
        );

        assertEquals(
            List.of(
                "start",
                "provider",
                "complete"
            ),
            sequence
        );

        assertEquals(
            1,
            providerCalls.get()
        );

        assertEquals(
            1,
            start.calls
        );

        assertEquals(
            claimed.id(),
            start.outboxId
        );

        assertEquals(
            WORKER_ID,
            start.workerId
        );

        assertEquals(
            NOW,
            start.startedAt
        );

        assertEquals(
            1,
            completion.calls
        );

        assertSame(
            start.handle,
            completion.attempt
        );

        assertEquals(
            WORKER_ID,
            completion.workerId
        );

        assertSame(
            configuredResult,
            completion.result
        );

        assertEquals(
            NOW,
            completion.completedAt
        );
    }

    @Test
    void shouldCompleteTransientChannelResultAgainstStartedAttempt() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        RecordingStart start =
            new RecordingStart(
                claimed,
                new ArrayList<>()
            );

        RecordingCompletion completion =
            new RecordingCompletion(
                claimed,
                new ArrayList<>()
            );

        PublicationOutboxWorkerRunResult result =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                channel ->
                    command ->
                        PublicationResult.failedTransient(
                            "FAKE_TEMPORARY_FAILURE"
                        ),
                start,
                completion,
                CLOCK
            ).runOnce();

        assertEquals(
            PublicationOutboxStatus.FAILED_TRANSIENT,
            result.finalStatus()
        );

        assertEquals(
            PublicationResultStatus.FAILED_TRANSIENT,
            completion.result.status()
        );

        assertSame(
            start.handle,
            completion.attempt
        );
    }

    @Test
    void shouldCompletePermanentChannelResultAgainstStartedAttempt() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        RecordingStart start =
            new RecordingStart(
                claimed,
                new ArrayList<>()
            );

        RecordingCompletion completion =
            new RecordingCompletion(
                claimed,
                new ArrayList<>()
            );

        PublicationOutboxWorkerRunResult result =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                channel ->
                    command ->
                        PublicationResult.failedPermanent(
                            "FAKE_INVALID_DESTINATION"
                        ),
                start,
                completion,
                CLOCK
            ).runOnce();

        assertEquals(
            PublicationOutboxStatus.FAILED_PERMANENT,
            result.finalStatus()
        );

        assertEquals(
            PublicationResultStatus.FAILED_PERMANENT,
            completion.result.status()
        );

        assertSame(
            start.handle,
            completion.attempt
        );
    }

    @Test
    void shouldCompleteDeliveryUnknownChannelResultAgainstStartedAttempt() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        RecordingStart start =
            new RecordingStart(
                claimed,
                new ArrayList<>()
            );

        RecordingCompletion completion =
            new RecordingCompletion(
                claimed,
                new ArrayList<>()
            );

        PublicationOutboxWorkerRunResult result =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                channel ->
                    command ->
                        PublicationResult.deliveryUnknown(
                            "FAKE_DELIVERY_OUTCOME_UNKNOWN"
                        ),
                start,
                completion,
                CLOCK
            ).runOnce();

        assertEquals(
            PublicationOutboxStatus.DELIVERY_UNKNOWN,
            result.finalStatus()
        );

        assertEquals(
            PublicationResultStatus.DELIVERY_UNKNOWN,
            completion.result.status()
        );

        assertSame(
            start.handle,
            completion.attempt
        );
    }

    @Test
    void channelResolutionFailureShouldNotCreateAttempt() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        AtomicInteger startCalls =
            new AtomicInteger();

        PublicationAttemptStartPort start =
            (
                outboxId,
                workerId,
                startedAt
            ) -> {

                startCalls.incrementAndGet();

                throw new AssertionError(
                    "STARTED must not exist before channel resolution"
                );
            };

        AtomicInteger completionCalls =
            new AtomicInteger();

        PublicationAttemptCompletionPort completion =
            (
                attempt,
                workerId,
                result,
                completedAt
            ) -> {

                completionCalls.incrementAndGet();

                throw new AssertionError(
                    "completion must not be called"
                );
            };

        PublicationChannelResolver resolver =
            channel -> {
                throw new IllegalStateException(
                    "channel not configured"
                );
            };

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                resolver,
                start,
                completion,
                CLOCK
            );

        assertThrows(
            IllegalStateException.class,
            worker::runOnce
        );

        assertEquals(
            0,
            startCalls.get()
        );

        assertEquals(
            0,
            completionCalls.get()
        );
    }

    @Test
    void attemptStartFailureShouldPreventProviderCall() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        AtomicInteger providerCalls =
            new AtomicInteger();

        PublicationChannel channel =
            command -> {

                providerCalls.incrementAndGet();

                return PublicationResult.success(
                    "must-not-be-sent"
                );
            };

        PublicationAttemptStartPort failingStart =
            (
                outboxId,
                workerId,
                startedAt
            ) -> {
                throw new IllegalStateException(
                    "failed to durably persist STARTED"
                );
            };

        AtomicInteger completionCalls =
            new AtomicInteger();

        PublicationAttemptCompletionPort completion =
            (
                attempt,
                workerId,
                result,
                completedAt
            ) -> {

                completionCalls.incrementAndGet();

                throw new AssertionError(
                    "completion must not be called"
                );
            };

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                logicalChannel ->
                    channel,
                failingStart,
                completion,
                CLOCK
            );

        assertThrows(
            IllegalStateException.class,
            worker::runOnce
        );

        assertEquals(
            0,
            providerCalls.get()
        );

        assertEquals(
            0,
            completionCalls.get()
        );
    }

    @Test
    void providerExceptionShouldLeaveStartedAttemptWithoutCompletion() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        RecordingStart start =
            new RecordingStart(
                claimed,
                new ArrayList<>()
            );

        PublicationChannel failingChannel =
            command -> {
                throw new IllegalStateException(
                    "unexpected provider adapter failure"
                );
            };

        AtomicInteger completionCalls =
            new AtomicInteger();

        PublicationAttemptCompletionPort completion =
            (
                attempt,
                workerId,
                result,
                completedAt
            ) -> {

                completionCalls.incrementAndGet();

                throw new AssertionError(
                    "completion must not be called after provider exception"
                );
            };

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                channel ->
                    failingChannel,
                start,
                completion,
                CLOCK
            );

        assertThrows(
            IllegalStateException.class,
            worker::runOnce
        );

        assertEquals(
            1,
            start.calls
        );

        assertEquals(
            0,
            completionCalls.get()
        );
    }

    @Test
    void completionFailureAfterProviderShouldNotRepublish() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        RecordingStart start =
            new RecordingStart(
                claimed,
                new ArrayList<>()
            );

        AtomicInteger providerCalls =
            new AtomicInteger();

        PublicationChannel channel =
            command -> {

                providerCalls.incrementAndGet();

                return PublicationResult.success(
                    "provider-reference"
                );
            };

        AtomicInteger completionCalls =
            new AtomicInteger();

        PublicationAttemptCompletionPort failingCompletion =
            (
                attempt,
                workerId,
                result,
                completedAt
            ) -> {

                completionCalls.incrementAndGet();

                assertSame(
                    start.handle,
                    attempt
                );

                throw new IllegalStateException(
                    "database acknowledgement failed"
                );
            };

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                logicalChannel ->
                    channel,
                start,
                failingCompletion,
                CLOCK
            );

        assertThrows(
            IllegalStateException.class,
            worker::runOnce
        );

        assertEquals(
            1,
            start.calls
        );

        assertEquals(
            1,
            providerCalls.get()
        );

        assertEquals(
            1,
            completionCalls.get()
        );
    }

    private PublicationOutboxItem processingItem() {

        return new PublicationOutboxItem(
            100L,
            200L,
            300L,
            1,
            "FAKE",
            "fake-destination",
            "Oferta totalmente pronta",
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
            WORKER_ID,
            NOW.minusHours(
                1
            ),
            NOW,
            null
        );
    }

    private static final class RecordingQueue
        implements PublicationOutboxQueuePort {

        private PublicationOutboxItem nextItem;

        private int claimCalls;

        private String lastWorkerId;

        private OffsetDateTime lastClaimedAt;

        @Override
        public Optional<PublicationOutboxItem> claimNext(
            String workerId,
            OffsetDateTime claimedAt
        ) {

            claimCalls++;

            lastWorkerId =
                workerId;

            lastClaimedAt =
                claimedAt;

            return Optional.ofNullable(
                nextItem
            );
        }

        @Override
        public int recoverExpiredLeases(
            OffsetDateTime lockedBefore,
            OffsetDateTime recoveredAt
        ) {

            return 0;
        }
    }

    private static final class RecordingStart
        implements PublicationAttemptStartPort {

        private final PublicationOutboxItem claimed;

        private final List<String> sequence;

        private int calls;

        private long outboxId;

        private String workerId;

        private OffsetDateTime startedAt;

        private PublicationAttemptHandle handle;

        private RecordingStart(
            PublicationOutboxItem claimed,
            List<String> sequence
        ) {

            this.claimed =
                claimed;

            this.sequence =
                sequence;
        }

        @Override
        public PublicationAttemptHandle start(
            long outboxId,
            String workerId,
            OffsetDateTime startedAt
        ) {

            sequence.add(
                "start"
            );

            calls++;

            this.outboxId =
                outboxId;

            this.workerId =
                workerId;

            this.startedAt =
                startedAt;

            this.handle =
                new PublicationAttemptHandle(
                    900L,
                    claimed.id(),
                    1,
                    startedAt
                );

            return handle;
        }
    }

    private static final class RecordingCompletion
        implements PublicationAttemptCompletionPort {

        private final PublicationOutboxItem claimed;

        private final List<String> sequence;

        private int calls;

        private PublicationAttemptHandle attempt;

        private String workerId;

        private PublicationResult result;

        private OffsetDateTime completedAt;

        private RecordingCompletion(
            PublicationOutboxItem claimed,
            List<String> sequence
        ) {

            this.claimed =
                claimed;

            this.sequence =
                sequence;
        }

        @Override
        public PublicationOutboxItem complete(
            PublicationAttemptHandle attempt,
            String workerId,
            PublicationResult result,
            OffsetDateTime completedAt
        ) {

            sequence.add(
                "complete"
            );

            calls++;

            this.attempt =
                attempt;

            this.workerId =
                workerId;

            this.result =
                result;

            this.completedAt =
                completedAt;

            PublicationOutboxStatus finalStatus =
                switch (result.status()) {

                    case SUCCESS ->
                        PublicationOutboxStatus.SUCCEEDED;

                    case FAILED_TRANSIENT ->
                        PublicationOutboxStatus.FAILED_TRANSIENT;

                    case FAILED_PERMANENT ->
                        PublicationOutboxStatus.FAILED_PERMANENT;

                    case DELIVERY_UNKNOWN ->
                        PublicationOutboxStatus.DELIVERY_UNKNOWN;
                };

            return new PublicationOutboxItem(
                claimed.id(),
                claimed.publicationId(),
                claimed.selectionRunId(),
                claimed.selectionPosition(),
                claimed.channel(),
                claimed.destination(),
                claimed.content(),
                claimed.quotaProfileVersion(),
                claimed.quotaDate(),
                finalStatus,
                claimed.availableAt(),
                null,
                null,
                claimed.createdAt(),
                completedAt,
                completedAt
            );
        }
    }
}
