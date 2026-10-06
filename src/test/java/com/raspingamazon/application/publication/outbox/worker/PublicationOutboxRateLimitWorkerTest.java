package com.raspingamazon.application.publication.outbox.worker;

import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.outbox.PublicationAttemptHandle;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.port.PublicationAttemptCompletionPort;
import com.raspingamazon.application.publication.outbox.port.PublicationAttemptStartPort;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxQueuePort;
import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitReservation;
import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitRule;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationOutboxRateLimitWorkerTest {

    private static final String WORKER_ID =
        "publication-rate-limit-worker";

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-01T23:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-10-01T23:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void deferredAdmissionShouldNotStartAttemptOrCallProvider() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue(
                claimed
            );

        AtomicInteger providerCalls =
            new AtomicInteger();

        PublicationChannel channel =
            command -> {

                providerCalls.incrementAndGet();

                return PublicationResult.success(
                    "must-not-happen"
                );
            };

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
                    "STARTED must not be created when rate limit defers"
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
                    "completion must not be called "
                        + "when provider was not called"
                );
            };

        OffsetDateTime retryAt =
            NOW.plusSeconds(
                2
            );

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                logicalChannel ->
                    channel,
                start,
                completion,
                CLOCK,
                logicalChannel ->
                    Optional.of(
                        new PublicationRateLimitRule(
                            "TELEGRAM_BOT_API",
                            Duration.ofSeconds(
                                2
                            )
                        )
                    ),
                (
                    integrationKey,
                    minimumInterval,
                    requestedAt
                ) ->
                    new PublicationRateLimitReservation(
                        integrationKey,
                        requestedAt,
                        retryAt,
                        retryAt
                    )
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
            PublicationOutboxStatus.PENDING,
            result.finalStatus()
        );

        assertEquals(
            0,
            startCalls.get()
        );

        assertEquals(
            0,
            providerCalls.get()
        );

        assertEquals(
            0,
            completionCalls.get()
        );

        assertEquals(
            1,
            queue.deferCalls
        );

        assertEquals(
            claimed.id(),
            queue.deferredOutboxId
        );

        assertEquals(
            WORKER_ID,
            queue.deferredWorkerId
        );

        assertEquals(
            retryAt,
            queue.deferredAvailableAt
        );

        assertEquals(
            NOW,
            queue.deferredAt
        );
    }

    @Test
    void immediateAdmissionShouldStartThenPublishThenComplete() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue(
                claimed
            );

        RecordingStart start =
            new RecordingStart(
                claimed
            );

        AtomicInteger providerCalls =
            new AtomicInteger();

        PublicationChannel channel =
            command -> {

                assertEquals(
                    1,
                    start.calls
                );

                providerCalls.incrementAndGet();

                return PublicationResult.success(
                    "telegram-message-123"
                );
            };

        RecordingCompletion completion =
            new RecordingCompletion(
                claimed
            );

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                logicalChannel ->
                    channel,
                start,
                completion,
                CLOCK,
                logicalChannel ->
                    Optional.of(
                        new PublicationRateLimitRule(
                            "TELEGRAM_BOT_API",
                            Duration.ofSeconds(
                                2
                            )
                        )
                    ),
                (
                    integrationKey,
                    minimumInterval,
                    requestedAt
                ) ->
                    new PublicationRateLimitReservation(
                        integrationKey,
                        requestedAt,
                        requestedAt,
                        requestedAt.plus(
                            minimumInterval
                        )
                    )
            );

        PublicationOutboxWorkerRunResult result =
            worker.runOnce();

        assertEquals(
            PublicationOutboxStatus.SUCCEEDED,
            result.finalStatus()
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
            completion.calls
        );

        assertEquals(
            start.handle,
            completion.attempt
        );

        assertEquals(
            0,
            queue.deferCalls
        );
    }

    @Test
    void disabledRateLimitShouldStillRequireDurableAttemptBarrier() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue(
                claimed
            );

        RecordingStart start =
            new RecordingStart(
                claimed
            );

        AtomicInteger providerCalls =
            new AtomicInteger();

        RecordingCompletion completion =
            new RecordingCompletion(
                claimed
            );

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                logicalChannel ->
                    command -> {

                        assertEquals(
                            1,
                            start.calls
                        );

                        providerCalls.incrementAndGet();

                        return PublicationResult.success(
                            "provider-reference"
                        );
                    },
                start,
                completion,
                CLOCK
            );

        PublicationOutboxWorkerRunResult result =
            worker.runOnce();

        assertEquals(
            PublicationOutboxStatus.SUCCEEDED,
            result.finalStatus()
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
            completion.calls
        );

        assertEquals(
            start.handle,
            completion.attempt
        );

        assertEquals(
            0,
            queue.deferCalls
        );
    }

    private PublicationOutboxItem processingItem() {

        return new PublicationOutboxItem(
            100L,
            200L,
            300L,
            1,
            "TELEGRAM",
            "@offers",
            "Oferta pronta",
            "PUBLICATION_QUOTA_V1",
            LocalDate.of(
                2026,
                10,
                1
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

        private final PublicationOutboxItem claimed;

        private int deferCalls;

        private long deferredOutboxId;

        private String deferredWorkerId;

        private OffsetDateTime deferredAvailableAt;

        private OffsetDateTime deferredAt;

        private RecordingQueue(
            PublicationOutboxItem claimed
        ) {

            this.claimed =
                claimed;
        }

        @Override
        public Optional<PublicationOutboxItem> claimNext(
            String workerId,
            OffsetDateTime claimedAt
        ) {

            return Optional.of(
                claimed
            );
        }

        @Override
        public PublicationOutboxItem deferClaimed(
            long outboxId,
            String workerId,
            OffsetDateTime availableAt,
            OffsetDateTime deferredAt
        ) {

            deferCalls++;

            deferredOutboxId =
                outboxId;

            deferredWorkerId =
                workerId;

            deferredAvailableAt =
                availableAt;

            this.deferredAt =
                deferredAt;

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
                PublicationOutboxStatus.PENDING,
                availableAt,
                null,
                null,
                claimed.createdAt(),
                deferredAt,
                null
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

        private int calls;

        private PublicationAttemptHandle handle;

        private RecordingStart(
            PublicationOutboxItem claimed
        ) {

            this.claimed =
                claimed;
        }

        @Override
        public PublicationAttemptHandle start(
            long outboxId,
            String workerId,
            OffsetDateTime startedAt
        ) {

            calls++;

            handle =
                new PublicationAttemptHandle(
                    700L,
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

        private int calls;

        private PublicationAttemptHandle attempt;

        private RecordingCompletion(
            PublicationOutboxItem claimed
        ) {

            this.claimed =
                claimed;
        }

        @Override
        public PublicationOutboxItem complete(
            PublicationAttemptHandle attempt,
            String workerId,
            PublicationResult result,
            OffsetDateTime completedAt
        ) {

            calls++;

            this.attempt =
                attempt;

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
                PublicationOutboxStatus.SUCCEEDED,
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
