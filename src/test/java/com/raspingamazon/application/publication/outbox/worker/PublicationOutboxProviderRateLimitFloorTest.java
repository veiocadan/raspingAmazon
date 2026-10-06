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
import com.raspingamazon.application.publication.ratelimit.port.PublicationRateLimitReservationPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationOutboxProviderRateLimitFloorTest {

    private static final String WORKER_ID =
        "publication-provider-floor-worker";

    private static final String INTEGRATION_KEY =
        "TELEGRAM_BOT_API";

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-06T21:00:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-10-06T21:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void shouldPersistProviderFloorBeforeCompletingAttempt() {

        PublicationOutboxItem claimed =
            processingItem();

        List<String> sequence =
            new ArrayList<>();

        RecordingRateLimitPort rateLimitPort =
            new RecordingRateLimitPort(
                sequence
            );

        PublicationAttemptStartPort startPort =
            (
                outboxId,
                workerId,
                startedAt
            ) -> {

                sequence.add(
                    "start"
                );

                return new PublicationAttemptHandle(
                    900L,
                    outboxId,
                    1,
                    startedAt
                );
            };

        OffsetDateTime providerFloor =
            NOW.plusMinutes(
                2
            );

        PublicationChannel channel =
            command -> {

                sequence.add(
                    "provider"
                );

                return PublicationResult
                    .failedTransientWithRetryNotBefore(
                        "TELEGRAM_RATE_LIMITED",
                        providerFloor
                    );
            };

        AtomicReference<PublicationResult> completedResult =
            new AtomicReference<>();

        PublicationAttemptCompletionPort completionPort =
            (
                attempt,
                workerId,
                result,
                completedAt
            ) -> {

                sequence.add(
                    "complete"
                );

                completedResult.set(
                    result
                );

                return completedItem(
                    claimed,
                    PublicationOutboxStatus.FAILED_TRANSIENT,
                    completedAt
                );
            };

        PublicationOutboxWorkerRunResult runResult =
            new PublicationOutboxWorker(
                WORKER_ID,
                queueWith(
                    claimed
                ),
                channelName ->
                    channel,
                startPort,
                completionPort,
                CLOCK,
                channelName ->
                    Optional.of(
                        rateLimitRule()
                    ),
                rateLimitPort
            ).runOnce();

        assertEquals(
            List.of(
                "reserve",
                "start",
                "provider",
                "extend",
                "complete"
            ),
            sequence
        );

        assertEquals(
            INTEGRATION_KEY,
            rateLimitPort.extendedIntegrationKey
        );

        assertEquals(
            providerFloor,
            rateLimitPort.requestedNotBefore
        );

        assertEquals(
            NOW,
            rateLimitPort.observedAt
        );

        assertEquals(
            providerFloor,
            completedResult.get()
                .retryNotBefore()
        );

        assertEquals(
            PublicationOutboxStatus.FAILED_TRANSIENT,
            runResult.finalStatus()
        );
    }

    @Test
    void shouldNotExtendSharedLimiterWithoutProviderFloor() {

        PublicationOutboxItem claimed =
            processingItem();

        RecordingRateLimitPort rateLimitPort =
            new RecordingRateLimitPort(
                new ArrayList<>()
            );

        AtomicInteger completionCalls =
            new AtomicInteger();

        new PublicationOutboxWorker(
            WORKER_ID,
            queueWith(
                claimed
            ),
            channelName ->
                command ->
                    PublicationResult.failedTransient(
                        "TEMPORARY_FAILURE"
                    ),
            successfulStart(),
            (
                attempt,
                workerId,
                result,
                completedAt
            ) -> {

                completionCalls.incrementAndGet();

                return completedItem(
                    claimed,
                    PublicationOutboxStatus.FAILED_TRANSIENT,
                    completedAt
                );
            },
            CLOCK,
            channelName ->
                Optional.of(
                    rateLimitRule()
                ),
            rateLimitPort
        ).runOnce();

        assertEquals(
            0,
            rateLimitPort.extendCalls
        );

        assertEquals(
            1,
            completionCalls.get()
        );
    }

    @Test
    void providerFloorPersistenceFailureShouldPreventCompletion() {

        PublicationOutboxItem claimed =
            processingItem();

        AtomicInteger startCalls =
            new AtomicInteger();

        AtomicInteger completionCalls =
            new AtomicInteger();

        PublicationRateLimitReservationPort failingRateLimitPort =
            new PublicationRateLimitReservationPort() {

                @Override
                public PublicationRateLimitReservation reserve(
                    String integrationKey,
                    Duration minimumInterval,
                    OffsetDateTime requestedAt
                ) {

                    return new PublicationRateLimitReservation(
                        integrationKey,
                        requestedAt,
                        requestedAt,
                        requestedAt.plus(
                            minimumInterval
                        )
                    );
                }

                @Override
                public OffsetDateTime extendNotBefore(
                    String integrationKey,
                    OffsetDateTime notBefore,
                    OffsetDateTime observedAt
                ) {

                    throw new IllegalStateException(
                        "database rate-limit floor failure"
                    );
                }
            };

        PublicationOutboxWorker worker =
            new PublicationOutboxWorker(
                WORKER_ID,
                queueWith(
                    claimed
                ),
                channelName ->
                    command ->
                        PublicationResult
                            .failedTransientWithRetryNotBefore(
                                "TELEGRAM_RATE_LIMITED",
                                NOW.plusMinutes(
                                    2
                                )
                            ),
                (
                    outboxId,
                    workerId,
                    startedAt
                ) -> {

                    startCalls.incrementAndGet();

                    return new PublicationAttemptHandle(
                        901L,
                        outboxId,
                        1,
                        startedAt
                    );
                },
                (
                    attempt,
                    workerId,
                    result,
                    completedAt
                ) -> {

                    completionCalls.incrementAndGet();

                    throw new AssertionError(
                        "completion must not occur after "
                            + "provider floor persistence failure"
                    );
                },
                CLOCK,
                channelName ->
                    Optional.of(
                        rateLimitRule()
                    ),
                failingRateLimitPort
            );

        assertThrows(
            IllegalStateException.class,
            worker::runOnce
        );

        assertEquals(
            1,
            startCalls.get()
        );

        assertEquals(
            0,
            completionCalls.get()
        );
    }

    private PublicationRateLimitRule rateLimitRule() {

        return new PublicationRateLimitRule(
            INTEGRATION_KEY,
            Duration.ofSeconds(
                1
            )
        );
    }

    private PublicationAttemptStartPort successfulStart() {

        return (
            outboxId,
            workerId,
            startedAt
        ) ->
            new PublicationAttemptHandle(
                902L,
                outboxId,
                1,
                startedAt
            );
    }

    private PublicationOutboxQueuePort queueWith(
        PublicationOutboxItem item
    ) {

        return new PublicationOutboxQueuePort() {

            private boolean claimed;

            @Override
            public Optional<PublicationOutboxItem> claimNext(
                String workerId,
                OffsetDateTime claimedAt
            ) {

                if (claimed) {
                    return Optional.empty();
                }

                claimed =
                    true;

                return Optional.of(
                    item
                );
            }

            @Override
            public int recoverExpiredLeases(
                OffsetDateTime lockedBefore,
                OffsetDateTime recoveredAt
            ) {

                return 0;
            }
        };
    }

    private PublicationOutboxItem processingItem() {

        return new PublicationOutboxItem(
            100L,
            200L,
            300L,
            1,
            "TELEGRAM",
            "fake-destination",
            "Oferta pronta",
            "PUBLICATION_QUOTA_V1",
            LocalDate.of(
                2026,
                10,
                6
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

    private PublicationOutboxItem completedItem(
        PublicationOutboxItem claimed,
        PublicationOutboxStatus status,
        OffsetDateTime completedAt
    ) {

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
            status,
            claimed.availableAt(),
            null,
            null,
            claimed.createdAt(),
            completedAt,
            completedAt
        );
    }

    private static final class RecordingRateLimitPort
        implements PublicationRateLimitReservationPort {

        private final List<String> sequence;

        private int extendCalls;

        private String extendedIntegrationKey;

        private OffsetDateTime requestedNotBefore;

        private OffsetDateTime observedAt;

        private RecordingRateLimitPort(
            List<String> sequence
        ) {

            this.sequence =
                sequence;
        }

        @Override
        public PublicationRateLimitReservation reserve(
            String integrationKey,
            Duration minimumInterval,
            OffsetDateTime requestedAt
        ) {

            sequence.add(
                "reserve"
            );

            return new PublicationRateLimitReservation(
                integrationKey,
                requestedAt,
                requestedAt,
                requestedAt.plus(
                    minimumInterval
                )
            );
        }

        @Override
        public OffsetDateTime extendNotBefore(
            String integrationKey,
            OffsetDateTime notBefore,
            OffsetDateTime observedAt
        ) {

            sequence.add(
                "extend"
            );

            extendCalls++;

            extendedIntegrationKey =
                integrationKey;

            requestedNotBefore =
                notBefore;

            this.observedAt =
                observedAt;

            return notBefore;
        }
    }
}
