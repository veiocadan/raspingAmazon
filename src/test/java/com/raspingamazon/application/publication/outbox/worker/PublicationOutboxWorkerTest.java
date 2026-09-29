package com.raspingamazon.application.publication.outbox.worker;

import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationChannelResolver;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.channel.PublicationResultStatus;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxCompletionPort;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxQueuePort;
import com.raspingamazon.infrastructure.publication.channel.FakePublicationChannel;
import com.raspingamazon.infrastructure.publication.channel.MapPublicationChannelResolver;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

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
    void shouldReturnIdleWhenNoOutboxIsAvailable() {

        RecordingQueue queue =
            new RecordingQueue();

        PublicationChannelResolver resolver =
            channel -> {
                throw new AssertionError(
                    "resolver must not be called"
                );
            };

        PublicationOutboxCompletionPort completionPort =
            (outboxId, workerId, result, completedAt) -> {
                throw new AssertionError(
                    "completion must not be called"
                );
            };

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                resolver,
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
    void shouldPublishThroughFakeChannelAndPersistSuccess() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        PublicationResult configuredResult =
            PublicationResult.success(
                "fake-provider-reference"
            );

        FakePublicationChannel fakeChannel =
            new FakePublicationChannel(
                configuredResult
            );

        MapPublicationChannelResolver resolver =
            new MapPublicationChannelResolver(
                Map.of(
                    "FAKE",
                    fakeChannel
                )
            );

        RecordingCompletion completion =
            new RecordingCompletion(
                claimed
            );

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                resolver,
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
            1,
            fakeChannel.callCount()
        );

        assertEquals(
            claimed.publicationId(),
            fakeChannel.lastCommand()
                .orElseThrow()
                .publicationId()
        );

        assertEquals(
            claimed.channel(),
            fakeChannel.lastCommand()
                .orElseThrow()
                .channel()
        );

        assertEquals(
            claimed.destination(),
            fakeChannel.lastCommand()
                .orElseThrow()
                .destination()
        );

        assertEquals(
            claimed.content(),
            fakeChannel.lastCommand()
                .orElseThrow()
                .content()
        );

        assertEquals(
            1,
            completion.calls
        );

        assertEquals(
            claimed.id(),
            completion.outboxId
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
    void shouldPersistTransientChannelResult() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        FakePublicationChannel fakeChannel =
            new FakePublicationChannel(
                PublicationResult.failedTransient(
                    "FAKE_TEMPORARY_FAILURE"
                )
            );

        RecordingCompletion completion =
            new RecordingCompletion(
                claimed
            );

        PublicationOutboxWorkerRunResult result =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                channel ->
                    fakeChannel,
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

        assertEquals(
            1,
            fakeChannel.callCount()
        );
    }

    @Test
    void shouldPersistPermanentChannelResult() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        FakePublicationChannel fakeChannel =
            new FakePublicationChannel(
                PublicationResult.failedPermanent(
                    "FAKE_INVALID_DESTINATION"
                )
            );

        RecordingCompletion completion =
            new RecordingCompletion(
                claimed
            );

        PublicationOutboxWorkerRunResult result =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                channel ->
                    fakeChannel,
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

        assertEquals(
            1,
            fakeChannel.callCount()
        );
    }

    @Test
    void shouldPropagateChannelResolutionFailureWithoutCompletingOutbox() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        RecordingCompletion completion =
            new RecordingCompletion(
                claimed
            );

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
                completion,
                CLOCK
            );

        assertThrows(
            IllegalStateException.class,
            worker::runOnce
        );

        assertEquals(
            0,
            completion.calls
        );
    }

    @Test
    void shouldPropagateUnexpectedChannelExceptionWithoutInventingResult() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        PublicationChannel failingChannel =
            command -> {
                throw new IllegalStateException(
                    "unexpected provider adapter failure"
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
                channel ->
                    failingChannel,
                completion,
                CLOCK
            );

        assertThrows(
            IllegalStateException.class,
            worker::runOnce
        );

        assertEquals(
            0,
            completion.calls
        );
    }

    @Test
    void shouldPropagateCompletionFailureAfterPublicationWithoutRepublishing() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingQueue queue =
            new RecordingQueue();

        queue.nextItem =
            claimed;

        FakePublicationChannel fakeChannel =
            new FakePublicationChannel(
                PublicationResult.success(
                    "provider-reference"
                )
            );

        PublicationOutboxCompletionPort failingCompletion =
            (outboxId, workerId, result, completedAt) -> {
                throw new IllegalStateException(
                    "database acknowledgement failed"
                );
            };

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queue,
                channel ->
                    fakeChannel,
                failingCompletion,
                CLOCK
            );

        assertThrows(
            IllegalStateException.class,
            worker::runOnce
        );

        /*
         * A mensagem já atravessou a fronteira do canal.
         *
         * O worker não chama publish novamente para tentar
         * "compensar" uma falha de persistência.
         */
        assertEquals(
            1,
            fakeChannel.callCount()
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

    private static final class RecordingCompletion
        implements PublicationOutboxCompletionPort {

        private final PublicationOutboxItem claimed;

        private int calls;

        private long outboxId;

        private String workerId;

        private PublicationResult result;

        private OffsetDateTime completedAt;

        private RecordingCompletion(
            PublicationOutboxItem claimed
        ) {

            this.claimed =
                claimed;
        }

        @Override
        public PublicationOutboxItem complete(
            long outboxId,
            String workerId,
            PublicationResult result,
            OffsetDateTime completedAt
        ) {

            calls++;

            this.outboxId =
                outboxId;

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
