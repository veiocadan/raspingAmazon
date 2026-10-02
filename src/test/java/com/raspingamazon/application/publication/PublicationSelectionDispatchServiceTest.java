package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.scheduling.PublicationCadenceSlotPlanner;
import com.raspingamazon.application.publication.selection.PublicationSelectionExecution;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;
import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaSnapshot;
import com.raspingamazon.domain.publication.selection.PublicationSelectionCandidate;
import com.raspingamazon.domain.publication.selection.PublicationSelectionDecision;
import com.raspingamazon.domain.publication.selection.PublicationSelectionDecisionStatus;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionRecency;
import com.raspingamazon.domain.publication.selection.PublicationSelectionResult;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationSelectionDispatchServiceTest {

    private static final long SELECTION_RUN_ID =
        700L;

    private static final Instant NOW =
        Instant.parse(
            "2026-09-30T12:10:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

    private static final ZoneId OPERATIONAL_ZONE =
        ZoneId.of(
            "America/Sao_Paulo"
        );

    @Test
    void shouldDispatchSelectedCandidatesIntoCadenceSlotsInPriorityOrder() {

        Publication firstPublication =
            createPublication(
                101L,
                11L,
                "B0AUTO0001",
                PublicationStatus.CREATED
            );

        Publication secondPublication =
            createPublication(
                102L,
                12L,
                "B0AUTO0002",
                PublicationStatus.CREATED
            );

        Map<Long, Publication> byEvaluation =
            Map.of(
                11L,
                firstPublication,
                12L,
                secondPublication
            );

        Map<Long, Publication> byPublication =
            Map.of(
                101L,
                firstPublication,
                102L,
                secondPublication
            );

        List<Long> generationCalls =
            new ArrayList<>();

        List<Long> readinessCalls =
            new ArrayList<>();

        AtomicReference<Optional<OffsetDateTime>>
            lastReserved =
            new AtomicReference<>(
                Optional.empty()
            );

        List<PublicationOutboxEnqueueRequest> enqueueRequests =
            new ArrayList<>();

        PublicationSelectionDispatchService service =
            cadenceService(
                dealEvaluationId -> {

                    generationCalls.add(
                        dealEvaluationId
                    );

                    return byEvaluation.get(
                        dealEvaluationId
                    );
                },
                publicationId -> {

                    readinessCalls.add(
                        publicationId
                    );

                    Publication publication =
                        byPublication.get(
                            publicationId
                        );

                    if (publication.status()
                        == PublicationStatus.CREATED) {

                        publication.markReady();
                    }

                    return publication;
                },
                request -> {

                    enqueueRequests.add(
                        request
                    );

                    /*
                     * Simula a persistência observada pela consulta
                     * da próxima iteração.
                     */
                    lastReserved.set(
                        Optional.of(
                            request.availableAt()
                        )
                    );

                    return PublicationOutboxEnqueueResult.enqueued(
                        900L
                            + enqueueRequests.size()
                    );
                },
                lastReserved,
                CLOCK
            );

        PublicationSelectionDispatchResult result =
            service.dispatch(
                selectionExecutionWithTwoSelectedCandidates()
            );

        assertEquals(
            List.of(
                11L,
                12L
            ),
            generationCalls
        );

        assertEquals(
            List.of(
                101L,
                102L
            ),
            readinessCalls
        );

        assertEquals(
            2,
            enqueueRequests.size()
        );

        assertEquals(
            localTime(
                10,
                0
            ),
            enqueueRequests.get(
                0
            ).availableAt()
        );

        assertEquals(
            localTime(
                12,
                0
            ),
            enqueueRequests.get(
                1
            ).availableAt()
        );

        for (PublicationOutboxEnqueueRequest request
            : enqueueRequests) {

            assertEquals(
                "CADENCE_TEST_V1",
                request.cadenceProfileVersion()
            );

            assertTrue(
                request.cadenceManaged()
            );

            assertEquals(
                SELECTION_RUN_ID,
                request.selectionRunId()
            );

            assertEquals(
                NOW,
                request.enqueuedAt()
                    .toInstant()
            );
        }

        assertEquals(
            2,
            result.selectedCandidateCount()
        );

        assertEquals(
            2,
            result.attemptedCount()
        );

        assertEquals(
            0,
            result.cadenceUnavailableCount()
        );

        assertEquals(
            0,
            result.unprocessedSelectedCount()
        );

        assertFalse(
            result.interruptedByReservationRevalidation()
        );

        assertEquals(
            2L,
            result.reservedCount()
        );
    }

    @Test
    void shouldNotGenerateCandidatesWhenNoCadenceSlotRemains() {

        Clock lateClock =
            Clock.fixed(
                Instant.parse(
                    "2026-10-01T02:00:00Z"
                ),
                ZoneOffset.UTC
            );

        AtomicInteger generationCalls =
            new AtomicInteger();

        AtomicInteger readinessCalls =
            new AtomicInteger();

        AtomicInteger enqueueCalls =
            new AtomicInteger();

        PublicationSelectionDispatchService service =
            cadenceService(
                dealEvaluationId -> {

                    generationCalls.incrementAndGet();

                    throw new AssertionError(
                        "generation must not be called"
                    );
                },
                publicationId -> {

                    readinessCalls.incrementAndGet();

                    throw new AssertionError(
                        "readiness must not be called"
                    );
                },
                request -> {

                    enqueueCalls.incrementAndGet();

                    throw new AssertionError(
                        "enqueue must not be called"
                    );
                },
                new AtomicReference<>(
                    Optional.empty()
                ),
                lateClock
            );

        PublicationSelectionDispatchResult result =
            service.dispatch(
                selectionExecutionWithTwoSelectedCandidates()
            );

        assertEquals(
            2,
            result.selectedCandidateCount()
        );

        assertEquals(
            0,
            result.attemptedCount()
        );

        assertEquals(
            2,
            result.cadenceUnavailableCount()
        );

        assertEquals(
            2,
            result.unprocessedSelectedCount()
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
    void shouldStopRemainingCandidatesWhenOutboxDetectsStaleSelection() {

        Publication publication =
            createPublication(
                101L,
                11L,
                "B0AUTO0001",
                PublicationStatus.CREATED
            );

        AtomicInteger generationCalls =
            new AtomicInteger();

        AtomicInteger readinessCalls =
            new AtomicInteger();

        AtomicInteger enqueueCalls =
            new AtomicInteger();

        PublicationSelectionDispatchService service =
            cadenceService(
                dealEvaluationId -> {

                    generationCalls.incrementAndGet();

                    return publication;
                },
                publicationId -> {

                    readinessCalls.incrementAndGet();

                    publication.markReady();

                    return publication;
                },
                request -> {

                    enqueueCalls.incrementAndGet();

                    return PublicationOutboxEnqueueResult
                        .staleSelectionResult();
                },
                new AtomicReference<>(
                    Optional.empty()
                ),
                CLOCK
            );

        PublicationSelectionDispatchResult result =
            service.dispatch(
                selectionExecutionWithTwoSelectedCandidates()
            );

        assertEquals(
            1,
            generationCalls.get()
        );

        assertEquals(
            1,
            readinessCalls.get()
        );

        assertEquals(
            1,
            enqueueCalls.get()
        );

        assertEquals(
            1,
            result.attemptedCount()
        );

        assertEquals(
            0,
            result.cadenceUnavailableCount()
        );

        assertEquals(
            1,
            result.unprocessedSelectedCount()
        );

        assertTrue(
            result.interruptedByReservationRevalidation()
        );

        assertEquals(
            1L,
            result.staleSelectionCount()
        );
    }

    @Test
    void shouldPreserveLegacyImmediateDispatchConstructor() {

        Publication publication =
            createPublication(
                101L,
                11L,
                "B0AUTO0001",
                PublicationStatus.CREATED
            );

        AtomicReference<PublicationOutboxEnqueueRequest> request =
            new AtomicReference<>();

        PublicationSelectionDispatchService service =
            new PublicationSelectionDispatchService(
                dealEvaluationId ->
                    publication,
                publicationId -> {

                    publication.markReady();

                    return publication;
                },
                enqueueRequest -> {

                    request.set(
                        enqueueRequest
                    );

                    return PublicationOutboxEnqueueResult
                        .alreadyEnqueued(
                            901L
                        );
                },
                CLOCK
            );

        PublicationSelectionDispatchResult result =
            service.dispatch(
                selectionExecutionWithOneSelectedCandidate()
            );

        assertFalse(
            request.get()
                .cadenceManaged()
        );

        assertEquals(
            NOW,
            request.get()
                .availableAt()
                .toInstant()
        );

        assertEquals(
            NOW,
            request.get()
                .enqueuedAt()
                .toInstant()
        );

        assertEquals(
            1L,
            result.reservedCount()
        );

        assertFalse(
            result.interruptedByReservationRevalidation()
        );
    }

    @Test
    void shouldRejectGeneratedPublicationFromDifferentEvaluation() {

        Publication wrongPublication =
            createPublication(
                101L,
                999L,
                "B0AUTO0999",
                PublicationStatus.CREATED
            );

        AtomicInteger readinessCalls =
            new AtomicInteger();

        AtomicInteger enqueueCalls =
            new AtomicInteger();

        PublicationSelectionDispatchService service =
            new PublicationSelectionDispatchService(
                dealEvaluationId ->
                    wrongPublication,
                publicationId -> {

                    readinessCalls.incrementAndGet();

                    return wrongPublication;
                },
                request -> {

                    enqueueCalls.incrementAndGet();

                    return PublicationOutboxEnqueueResult
                        .enqueued(
                            901L
                        );
                },
                CLOCK
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.dispatch(
                    selectionExecutionWithOneSelectedCandidate()
                )
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
    void shouldRejectReadinessResultThatIsNotReady() {

        Publication publication =
            createPublication(
                101L,
                11L,
                "B0AUTO0001",
                PublicationStatus.CREATED
            );

        AtomicInteger enqueueCalls =
            new AtomicInteger();

        PublicationSelectionDispatchService service =
            new PublicationSelectionDispatchService(
                dealEvaluationId ->
                    publication,
                publicationId ->
                    publication,
                request -> {

                    enqueueCalls.incrementAndGet();

                    return PublicationOutboxEnqueueResult
                        .enqueued(
                            901L
                        );
                },
                CLOCK
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.dispatch(
                    selectionExecutionWithOneSelectedCandidate()
                )
        );

        assertEquals(
            0,
            enqueueCalls.get()
        );
    }

    @Test
    void shouldDoNothingWhenSelectionContainsNoSelectedCandidate() {

        AtomicInteger generationCalls =
            new AtomicInteger();

        AtomicInteger readinessCalls =
            new AtomicInteger();

        AtomicInteger enqueueCalls =
            new AtomicInteger();

        PublicationSelectionDispatchService service =
            cadenceService(
                dealEvaluationId -> {

                    generationCalls.incrementAndGet();

                    throw new AssertionError(
                        "generation must not be called"
                    );
                },
                publicationId -> {

                    readinessCalls.incrementAndGet();

                    throw new AssertionError(
                        "readiness must not be called"
                    );
                },
                request -> {

                    enqueueCalls.incrementAndGet();

                    throw new AssertionError(
                        "enqueue must not be called"
                    );
                },
                new AtomicReference<>(
                    Optional.empty()
                ),
                CLOCK
            );

        PublicationSelectionDispatchResult result =
            service.dispatch(
                selectionExecutionWithoutSelectedCandidate()
            );

        assertEquals(
            0,
            result.selectedCandidateCount()
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

    private PublicationSelectionDispatchService cadenceService(
        PublicationGenerationUseCase generationUseCase,
        PublicationReadinessUseCase readinessUseCase,
        com.raspingamazon.application.publication.outbox.port
            .PublicationOutboxEnqueuePort enqueuePort,
        AtomicReference<Optional<OffsetDateTime>> lastReserved,
        Clock clock
    ) {

        return new PublicationSelectionDispatchService(
            generationUseCase,
            readinessUseCase,
            enqueuePort,
            (channel, destination) ->
                new PublicationCadenceProfile(
                    "CADENCE_TEST_V1",
                    Duration.ofHours(
                        2
                    ),
                    LocalTime.of(
                        8,
                        0
                    ),
                    LocalTime.of(
                        22,
                        0
                    ),
                    OPERATIONAL_ZONE
                ),
            (channel, destination, quotaDate) ->
                lastReserved.get(),
            new PublicationCadenceSlotPlanner(),
            clock
        );
    }

    private PublicationSelectionExecution
    selectionExecutionWithTwoSelectedCandidates() {

        PublicationSelectionCandidate first =
            candidate(
                11L,
                "B0AUTO0001",
                "90.0000"
            );

        PublicationSelectionCandidate second =
            candidate(
                12L,
                "B0AUTO0002",
                "80.0000"
            );

        List<PublicationSelectionDecision> decisions =
            List.of(
                selectedDecision(
                    second,
                    2
                ),
                selectedDecision(
                    first,
                    1
                )
            );

        return selectionExecution(
            2,
            0L,
            decisions
        );
    }

    private PublicationSelectionExecution
    selectionExecutionWithOneSelectedCandidate() {

        PublicationSelectionCandidate candidate =
            candidate(
                11L,
                "B0AUTO0001",
                "90.0000"
            );

        return selectionExecution(
            1,
            0L,
            List.of(
                selectedDecision(
                    candidate,
                    1
                )
            )
        );
    }

    private PublicationSelectionExecution
    selectionExecutionWithoutSelectedCandidate() {

        PublicationSelectionCandidate candidate =
            candidate(
                11L,
                "B0AUTO0001",
                "90.0000"
            );

        return selectionExecution(
            1,
            1L,
            List.of(
                new PublicationSelectionDecision(
                    candidate,
                    PublicationSelectionDecisionStatus
                        .NOT_SELECTED_DUE_TO_QUOTA,
                    PublicationSelectionRecency
                        .NEVER_SUCCESSFULLY_PUBLISHED,
                    1
                )
            )
        );
    }

    private PublicationSelectionExecution selectionExecution(
        int maxPublicationsPerDay,
        long occupiedSlots,
        List<PublicationSelectionDecision> decisions
    ) {

        PublicationSelectionProfile selectionProfile =
            new PublicationSelectionProfile(
                "SELECTION_TEST_V1",
                Duration.ZERO,
                Duration.ZERO
            );

        PublicationQuotaSnapshot quotaSnapshot =
            new PublicationQuotaSnapshot(
                "TELEGRAM",
                "@public_channel",
                LocalDate.of(
                    2026,
                    9,
                    30
                ),
                "QUOTA_TEST_V1",
                maxPublicationsPerDay,
                occupiedSlots
            );

        PublicationSelectionResult result =
            new PublicationSelectionResult(
                NOW,
                selectionProfile,
                quotaSnapshot,
                decisions
            );

        return new PublicationSelectionExecution(
            SELECTION_RUN_ID,
            result
        );
    }

    private PublicationSelectionDecision selectedDecision(
        PublicationSelectionCandidate candidate,
        int priorityPosition
    ) {

        return new PublicationSelectionDecision(
            candidate,
            PublicationSelectionDecisionStatus.SELECTED,
            PublicationSelectionRecency
                .NEVER_SUCCESSFULLY_PUBLISHED,
            priorityPosition
        );
    }

    private PublicationSelectionCandidate candidate(
        long dealEvaluationId,
        String asin,
        String score
    ) {

        return new PublicationSelectionCandidate(
            dealEvaluationId,
            new Asin(
                asin
            ),
            new BigDecimal(
                score
            ),
            "TELEGRAM",
            "@public_channel",
            null
        );
    }

    private Publication createPublication(
        long publicationId,
        long dealEvaluationId,
        String asin,
        PublicationStatus status
    ) {

        Product product =
            new Product(
                publicationId + 1000L,
                new Asin(
                    asin
                ),
                "Produto automático " + asin,
                null,
                "https://www.amazon.com.br/dp/" + asin
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                publicationId + 2000L,
                product,
                OffsetDateTime.parse(
                    "2026-09-30T18:00:00-03:00"
                ),
                Money.of(
                    "99.90"
                ),
                Money.of(
                    "129.90"
                ),
                null,
                null,
                4.8,
                1000L,
                "Amazon.com.br",
                "Amazon",
                SellerType.AMAZON,
                DeliveryType.AMAZON,
                "publication-selection-dispatch-test",
                List.of()
            );

        DealEvaluation evaluation =
            new DealEvaluation(
                dealEvaluationId,
                snapshot,
                true,
                null,
                "TEST_ELIGIBILITY_V1",
                null,
                List.of(
                    EvaluationRuleResult.passed(
                        "SELLER_IS_AMAZON",
                        "AMAZON",
                        "AMAZON"
                    )
                ),
                null,
                null,
                List.of(),
                null,
                null,
                OffsetDateTime.parse(
                    "2026-09-30T18:05:00-03:00"
                )
            );

        return new Publication(
            publicationId,
            evaluation,
            "AMAZON_PUBLICATION_V2",
            "AMAZON_COMMERCIAL_PRESENTATION_V2",
            "AMAZON_AFFILIATE_LINK_V2",
            """
            🔹**Produto automático**
            💰 De ~~R$ 129,90~~ por **R$ 99,90**!
            👇 Tá em Promo!
            🔗 https://www.amazon.com.br/dp/%s?tag=test-20
            """
                .formatted(
                    asin
                )
                .strip(),
            "https://www.amazon.com.br/dp/"
                + asin
                + "?tag=test-20",
            status,
            OffsetDateTime.parse(
                "2026-09-30T18:10:00-03:00"
            )
        );
    }

    private OffsetDateTime localTime(
        int hour,
        int minute
    ) {

        return LocalDate.of(
                2026,
                9,
                30
            )
            .atTime(
                hour,
                minute
            )
            .atZone(
                OPERATIONAL_ZONE
            )
            .toOffsetDateTime();
    }
}
