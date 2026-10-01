package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;
import com.raspingamazon.application.publication.selection.PublicationSelectionExecution;
import com.raspingamazon.application.publication.selection.PublicationSelectionService;
import com.raspingamazon.application.publication.selection.PublicationSelectionSourceCandidate;
import com.raspingamazon.application.publication.selection.port.PublicationQuotaProfileProvider;
import com.raspingamazon.application.publication.selection.port.PublicationQuotaUsageQueryPort;
import com.raspingamazon.application.publication.selection.port.PublicationSelectionAuditRepository;
import com.raspingamazon.application.publication.selection.port.PublicationSelectionProfileProvider;
import com.raspingamazon.application.publication.selection.port.PublicationSelectionSourceQueryPort;
import com.raspingamazon.application.publication.selection.port.SuccessfulPublicationHistoryQueryPort;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.evaluation.DealEvaluation;
import com.raspingamazon.domain.evaluation.EvaluationRuleResult;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.publication.Publication;
import com.raspingamazon.domain.publication.PublicationStatus;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionPolicy;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationProcessingRunDispatchServiceTest {

    private static final long PROCESSING_RUN_ID =
        500L;

    private static final long DEAL_EVALUATION_ID =
        11L;

    private static final long PUBLICATION_ID =
        101L;

    private static final long SELECTION_RUN_ID =
        700L;

    private static final String CHANNEL =
        "TELEGRAM";

    private static final String DESTINATION =
        "@public_channel";

    private static final Instant NOW =
        Instant.parse(
            "2026-09-30T23:45:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

    @Test
    void shouldLoadSelectDispatchAndPreserveAuditCorrelation() {

        PublicationSelectionSourceCandidate sourceCandidate =
            new PublicationSelectionSourceCandidate(
                DEAL_EVALUATION_ID,
                new Asin(
                    "B0RUN00001"
                ),
                new BigDecimal(
                    "91.2500"
                )
            );

        AtomicLong requestedProcessingRunId =
            new AtomicLong();

        PublicationSelectionSourceQueryPort sourceQueryPort =
            processingRunId -> {

                requestedProcessingRunId.set(
                    processingRunId
                );

                return List.of(
                    sourceCandidate
                );
            };

        AtomicReference<String> profileChannel =
            new AtomicReference<>();

        AtomicReference<String> profileDestination =
            new AtomicReference<>();

        PublicationSelectionProfileProvider
            selectionProfileProvider =
            (channel, destination) -> {

                profileChannel.set(
                    channel
                );

                profileDestination.set(
                    destination
                );

                return new PublicationSelectionProfile(
                    PublicationSelectionPolicy.VERSION,
                    Duration.ZERO,
                    Duration.ZERO
                );
            };

        PublicationQuotaProfileProvider quotaProfileProvider =
            (channel, destination) ->
                new PublicationQuotaProfile(
                    "PROCESSING_RUN_QUOTA_V1",
                    1,
                    ZoneId.of(
                        "America/Sao_Paulo"
                    )
                );

        PublicationQuotaUsageQueryPort quotaUsageQueryPort =
            (channel, destination, quotaDate) ->
                0L;

        SuccessfulPublicationHistoryQueryPort historyQueryPort =
            (asins, channel, destination) ->
                Map.of();

        AtomicReference<Object> savedSelectionResult =
            new AtomicReference<>();

        PublicationSelectionAuditRepository auditRepository =
            result -> {

                savedSelectionResult.set(
                    result
                );

                return SELECTION_RUN_ID;
            };

        PublicationSelectionService selectionService =
            new PublicationSelectionService(
                selectionProfileProvider,
                quotaProfileProvider,
                quotaUsageQueryPort,
                historyQueryPort,
                auditRepository,
                new PublicationSelectionPolicy()
            );

        Publication publication =
            createPublication(
                PUBLICATION_ID,
                DEAL_EVALUATION_ID,
                PublicationStatus.CREATED
            );

        AtomicInteger generationCalls =
            new AtomicInteger();

        PublicationGenerationUseCase generationUseCase =
            dealEvaluationId -> {

                generationCalls.incrementAndGet();

                assertEquals(
                    DEAL_EVALUATION_ID,
                    dealEvaluationId
                );

                return publication;
            };

        AtomicInteger readinessCalls =
            new AtomicInteger();

        PublicationReadinessUseCase readinessUseCase =
            publicationId -> {

                readinessCalls.incrementAndGet();

                assertEquals(
                    PUBLICATION_ID,
                    publicationId
                );

                if (publication.status()
                    == PublicationStatus.CREATED) {

                    publication.markReady();
                }

                return publication;
            };

        AtomicReference<PublicationOutboxEnqueueRequest>
            enqueueRequest =
            new AtomicReference<>();

        PublicationSelectionDispatchService dispatchService =
            new PublicationSelectionDispatchService(
                generationUseCase,
                readinessUseCase,
                request -> {

                    enqueueRequest.set(
                        request
                    );

                    return PublicationOutboxEnqueueResult.enqueued(
                        900L
                    );
                },
                CLOCK
            );

        PublicationProcessingRunDispatchService service =
            new PublicationProcessingRunDispatchService(
                sourceQueryPort,
                selectionService,
                dispatchService,
                CLOCK
            );

        PublicationProcessingRunDispatchResult result =
            service.process(
                PROCESSING_RUN_ID,
                "  " + CHANNEL + "  ",
                "  " + DESTINATION + "  "
            );

        assertEquals(
            PROCESSING_RUN_ID,
            requestedProcessingRunId.get()
        );

        assertEquals(
            CHANNEL,
            profileChannel.get()
        );

        assertEquals(
            DESTINATION,
            profileDestination.get()
        );

        assertEquals(
            PROCESSING_RUN_ID,
            result.processingRunId()
        );

        assertEquals(
            1,
            result.sourceCandidateCount()
        );

        assertEquals(
            SELECTION_RUN_ID,
            result.selectionExecution()
                .auditRunId()
        );

        assertSame(
            savedSelectionResult.get(),
            result.selectionExecution()
                .result()
        );

        assertEquals(
            1L,
            result.selectedCount()
        );

        assertEquals(
            SELECTION_RUN_ID,
            result.dispatchResult()
                .selectionRunId()
        );

        assertEquals(
            1,
            result.dispatchResult()
                .items()
                .size()
        );

        assertEquals(
            1L,
            result.reservedCount()
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
            PublicationStatus.READY,
            publication.status()
        );

        PublicationOutboxEnqueueRequest persistedRequest =
            enqueueRequest.get();

        assertEquals(
            PUBLICATION_ID,
            persistedRequest.publicationId()
        );

        /*
         * A identidade da SelectionRun produzida pelo serviço de
         * seleção é exatamente a usada pela outbox.
         */
        assertEquals(
            SELECTION_RUN_ID,
            persistedRequest.selectionRunId()
        );

        OffsetDateTime expectedDispatchAt =
            OffsetDateTime.ofInstant(
                NOW,
                ZoneOffset.UTC
            );

        assertEquals(
            expectedDispatchAt,
            persistedRequest.availableAt()
        );

        assertEquals(
            expectedDispatchAt,
            persistedRequest.enqueuedAt()
        );
    }

    @Test
    void shouldPersistEmptySelectionAndSkipGenerationAndEnqueue() {

        PublicationSelectionSourceQueryPort sourceQueryPort =
            processingRunId ->
                List.of();

        PublicationSelectionService selectionService =
            selectionService(
                800L
            );

        AtomicInteger generationCalls =
            new AtomicInteger();

        AtomicInteger readinessCalls =
            new AtomicInteger();

        AtomicInteger enqueueCalls =
            new AtomicInteger();

        PublicationSelectionDispatchService dispatchService =
            new PublicationSelectionDispatchService(
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
                CLOCK
            );

        PublicationProcessingRunDispatchService service =
            new PublicationProcessingRunDispatchService(
                sourceQueryPort,
                selectionService,
                dispatchService,
                CLOCK
            );

        PublicationProcessingRunDispatchResult result =
            service.process(
                PROCESSING_RUN_ID,
                CHANNEL,
                DESTINATION
            );

        assertEquals(
            0,
            result.sourceCandidateCount()
        );

        assertEquals(
            800L,
            result.selectionExecution()
                .auditRunId()
        );

        assertTrue(
            result.selectionExecution()
                .result()
                .decisions()
                .isEmpty()
        );

        assertTrue(
            result.dispatchResult()
                .items()
                .isEmpty()
        );

        assertEquals(
            0L,
            result.selectedCount()
        );

        assertEquals(
            0L,
            result.reservedCount()
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
    void shouldRejectInvalidProcessingRunBeforeQueryingSource() {

        AtomicInteger sourceCalls =
            new AtomicInteger();

        PublicationProcessingRunDispatchService service =
            new PublicationProcessingRunDispatchService(
                processingRunId -> {

                    sourceCalls.incrementAndGet();

                    return List.of();
                },
                selectionService(
                    900L
                ),
                emptyDispatchService(),
                CLOCK
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.process(
                    0L,
                    CHANNEL,
                    DESTINATION
                )
        );

        assertEquals(
            0,
            sourceCalls.get()
        );
    }

    @Test
    void shouldRejectBlankChannelBeforeQueryingSource() {

        AtomicInteger sourceCalls =
            new AtomicInteger();

        PublicationProcessingRunDispatchService service =
            new PublicationProcessingRunDispatchService(
                processingRunId -> {

                    sourceCalls.incrementAndGet();

                    return List.of();
                },
                selectionService(
                    900L
                ),
                emptyDispatchService(),
                CLOCK
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.process(
                    PROCESSING_RUN_ID,
                    "   ",
                    DESTINATION
                )
        );

        assertEquals(
            0,
            sourceCalls.get()
        );
    }

    @Test
    void shouldRejectNullSourceResult() {

        PublicationProcessingRunDispatchService service =
            new PublicationProcessingRunDispatchService(
                processingRunId ->
                    null,
                selectionService(
                    900L
                ),
                emptyDispatchService(),
                CLOCK
            );

        assertThrows(
            NullPointerException.class,
            () ->
                service.process(
                    PROCESSING_RUN_ID,
                    CHANNEL,
                    DESTINATION
                )
        );
    }

    private PublicationSelectionService selectionService(
        long auditRunId
    ) {

        PublicationSelectionProfileProvider selectionProfileProvider =
            (channel, destination) ->
                new PublicationSelectionProfile(
                    PublicationSelectionPolicy.VERSION,
                    Duration.ZERO,
                    Duration.ZERO
                );

        PublicationQuotaProfileProvider quotaProfileProvider =
            (channel, destination) ->
                new PublicationQuotaProfile(
                    "PROCESSING_RUN_QUOTA_V1",
                    1,
                    ZoneId.of(
                        "America/Sao_Paulo"
                    )
                );

        PublicationQuotaUsageQueryPort quotaUsageQueryPort =
            (channel, destination, quotaDate) ->
                0L;

        SuccessfulPublicationHistoryQueryPort historyQueryPort =
            (asins, channel, destination) ->
                Map.of();

        PublicationSelectionAuditRepository auditRepository =
            result ->
                auditRunId;

        return new PublicationSelectionService(
            selectionProfileProvider,
            quotaProfileProvider,
            quotaUsageQueryPort,
            historyQueryPort,
            auditRepository,
            new PublicationSelectionPolicy()
        );
    }

    private PublicationSelectionDispatchService
    emptyDispatchService() {

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

    private Publication createPublication(
        long publicationId,
        long dealEvaluationId,
        PublicationStatus status
    ) {

        String asin =
            "B0RUN00001";

        Product product =
            new Product(
                1001L,
                new Asin(
                    asin
                ),
                "Produto automático da ProcessingRun",
                null,
                "https://www.amazon.com.br/dp/" + asin
            );

        OfferSnapshot snapshot =
            new OfferSnapshot(
                2001L,
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
                "publication-processing-run-dispatch-test",
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
            🔹**Produto automático da ProcessingRun**
            💰 De ~~R$ 129,90~~ por **R$ 99,90**!
            👇 Tá em Promo!
            🔗 https://www.amazon.com.br/dp/B0RUN00001?tag=test-20
            """
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
}
