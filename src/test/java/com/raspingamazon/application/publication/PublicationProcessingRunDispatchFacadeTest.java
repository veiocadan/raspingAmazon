package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.selection.PublicationSelectionService;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionPolicy;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationProcessingRunDispatchFacadeTest {

    private static final Instant NOW =
        Instant.parse(
            "2026-09-30T22:15:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

    private static final String CHANNEL =
        "TELEGRAM";

    private static final String DESTINATION =
        "@public_channel";

    @Test
    void shouldProcessProcessingRunUsingConfiguredOperationalScope() {

        AtomicLong queriedProcessingRunId =
            new AtomicLong();

        AtomicInteger generationCalls =
            new AtomicInteger();

        AtomicInteger readinessCalls =
            new AtomicInteger();

        AtomicInteger enqueueCalls =
            new AtomicInteger();

        PublicationSelectionService selectionService =
            selectionService();

        PublicationSelectionDispatchService dispatchService =
            new PublicationSelectionDispatchService(
                dealEvaluationId -> {

                    generationCalls.incrementAndGet();

                    throw new AssertionError(
                        "generation must not be called "
                            + "when selection is empty"
                    );
                },
                publicationId -> {

                    readinessCalls.incrementAndGet();

                    throw new AssertionError(
                        "readiness must not be called "
                            + "when selection is empty"
                    );
                },
                request -> {

                    enqueueCalls.incrementAndGet();

                    return PublicationOutboxEnqueueResult.enqueued(
                        999L
                    );
                },
                CLOCK
            );

        PublicationProcessingRunDispatchService service =
            new PublicationProcessingRunDispatchService(
                processingRunId -> {

                    queriedProcessingRunId.set(
                        processingRunId
                    );

                    return List.of();
                },
                selectionService,
                dispatchService,
                CHANNEL,
                DESTINATION,
                CLOCK
            );

        PublicationSelectionDispatchResult result =
            service.process(
                123L
            );

        assertEquals(
            123L,
            queriedProcessingRunId.get()
        );

        assertEquals(
            700L,
            result.selectionRunId()
        );

        assertEquals(
            0,
            result.selectedCandidateCount()
        );

        assertEquals(
            0,
            result.attemptedCount()
        );

        assertTrue(
            result.items()
                .isEmpty()
        );

        assertEquals(
            0,
            generationCalls.get()
        );

        assertEquals(
            0,
            readinessCalls.get()
        );

        assertEquals(
            0,
            enqueueCalls.get()
        );
    }

    @Test
    void dispatchAliasShouldDelegateToProcess() {

        AtomicLong queriedProcessingRunId =
            new AtomicLong();

        PublicationProcessingRunDispatchService service =
            new PublicationProcessingRunDispatchService(
                processingRunId -> {

                    queriedProcessingRunId.set(
                        processingRunId
                    );

                    return List.of();
                },
                selectionService(),
                emptyDispatchService(),
                CHANNEL,
                DESTINATION,
                CLOCK
            );

        PublicationSelectionDispatchResult result =
            service.dispatch(
                456L
            );

        assertEquals(
            456L,
            queriedProcessingRunId.get()
        );

        assertEquals(
            700L,
            result.selectionRunId()
        );
    }

    @Test
    void shouldRejectInvalidProcessingRunIdBeforeQueryingSource() {

        AtomicInteger sourceCalls =
            new AtomicInteger();

        PublicationProcessingRunDispatchService service =
            new PublicationProcessingRunDispatchService(
                processingRunId -> {

                    sourceCalls.incrementAndGet();

                    return List.of();
                },
                selectionService(),
                emptyDispatchService(),
                CHANNEL,
                DESTINATION,
                CLOCK
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.process(
                    0L
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.process(
                    -1L
                )
        );

        assertEquals(
            0,
            sourceCalls.get()
        );
    }

    @Test
    void shouldTrimOperationalScopeAtConstruction() {

        AtomicInteger profileCalls =
            new AtomicInteger();

        PublicationSelectionService selectionService =
            new PublicationSelectionService(
                (channel, destination) -> {

                    profileCalls.incrementAndGet();

                    assertEquals(
                        CHANNEL,
                        channel
                    );

                    assertEquals(
                        DESTINATION,
                        destination
                    );

                    return new PublicationSelectionProfile(
                        "PUBLICATION_SELECTION_V1_CONFIG_V1",
                        Duration.ZERO,
                        Duration.ZERO
                    );
                },
                (channel, destination) ->
                    new PublicationQuotaProfile(
                        "QUOTA_TEST_V1",
                        7,
                        ZoneId.of(
                            "America/Sao_Paulo"
                        )
                    ),
                (channel, destination, quotaDate) ->
                    0L,
                (asins, channel, destination) ->
                    Map.of(),
                result ->
                    700L,
                new PublicationSelectionPolicy()
            );

        PublicationProcessingRunDispatchService service =
            new PublicationProcessingRunDispatchService(
                processingRunId ->
                    List.of(),
                selectionService,
                emptyDispatchService(),
                "   TELEGRAM   ",
                "   @public_channel   ",
                CLOCK
            );

        service.process(
            123L
        );

        assertEquals(
            1,
            profileCalls.get()
        );
    }

    private PublicationSelectionService selectionService() {

        return new PublicationSelectionService(
            (channel, destination) -> {

                assertEquals(
                    CHANNEL,
                    channel
                );

                assertEquals(
                    DESTINATION,
                    destination
                );

                return new PublicationSelectionProfile(
                    "PUBLICATION_SELECTION_V1_CONFIG_V1",
                    Duration.ZERO,
                    Duration.ZERO
                );
            },
            (channel, destination) -> {

                assertEquals(
                    CHANNEL,
                    channel
                );

                assertEquals(
                    DESTINATION,
                    destination
                );

                return new PublicationQuotaProfile(
                    "QUOTA_TEST_V1",
                    7,
                    ZoneId.of(
                        "America/Sao_Paulo"
                    )
                );
            },
            (channel, destination, quotaDate) -> {

                assertEquals(
                    CHANNEL,
                    channel
                );

                assertEquals(
                    DESTINATION,
                    destination
                );

                return 0L;
            },
            (asins, channel, destination) ->
                Map.of(),
            result -> {

                assertEquals(
                    NOW,
                    result.decidedAt()
                );

                return 700L;
            },
            new PublicationSelectionPolicy()
        );
    }

    private PublicationSelectionDispatchService emptyDispatchService() {

        return new PublicationSelectionDispatchService(
            dealEvaluationId -> {

                throw new AssertionError(
                    "generation must not be called"
                );
            },
            publicationId -> {

                throw new AssertionError(
                    "readiness must not be called"
                );
            },
            request -> {

                throw new AssertionError(
                    "enqueue must not be called"
                );
            },
            CLOCK
        );
    }
}
