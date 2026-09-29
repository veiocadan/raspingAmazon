package com.raspingamazon.application.publication.selection;

import com.raspingamazon.application.publication.selection.port.PublicationQuotaProfileProvider;
import com.raspingamazon.application.publication.selection.port.PublicationQuotaUsageQueryPort;
import com.raspingamazon.application.publication.selection.port.PublicationSelectionAuditRepository;
import com.raspingamazon.application.publication.selection.port.PublicationSelectionProfileProvider;
import com.raspingamazon.application.publication.selection.port.SuccessfulPublicationHistoryQueryPort;
import com.raspingamazon.domain.product.Asin;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionDecision;
import com.raspingamazon.domain.publication.selection.PublicationSelectionDecisionStatus;
import com.raspingamazon.domain.publication.selection.PublicationSelectionPolicy;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import com.raspingamazon.domain.publication.selection.PublicationSelectionResult;
import com.raspingamazon.domain.publication.selection.SuccessfulPublicationHistory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationSelectionServiceTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final String DESTINATION =
        "@phase18";

    private static final Instant NOW =
        Instant.parse(
            "2026-09-27T20:00:00Z"
        );

    private static final PublicationSelectionProfile
        SELECTION_PROFILE =
        new PublicationSelectionProfile(
            PublicationSelectionPolicy.VERSION,
            Duration.ofDays(
                2
            ),
            Duration.ofDays(
                7
            )
        );

    private static final PublicationQuotaProfile
        QUOTA_PROFILE =
        new PublicationQuotaProfile(
            "PUBLICATION_QUOTA_V1",
            2,
            ZoneId.of(
                "America/Sao_Paulo"
            )
        );

    @Test
    void shouldCoordinateSelectionAndPersistAudit() {

        PublicationSelectionSourceCandidate neverPublished =
            sourceCandidate(
                1L,
                "B0SVC18001",
                "70.0000"
            );

        PublicationSelectionSourceCandidate previouslyPublished =
            sourceCandidate(
                2L,
                "B0SVC18002",
                "99.0000"
            );

        SuccessfulPublicationHistory previousHistory =
            new SuccessfulPublicationHistory(
                previouslyPublished.asin(),
                NOW.minus(
                    Duration.ofDays(
                        20
                    )
                ),
                3L
            );

        RecordingHistoryQueryPort historyPort =
            new RecordingHistoryQueryPort(
                Map.of(
                    previouslyPublished.asin(),
                    previousHistory
                )
            );

        RecordingQuotaUsageQueryPort quotaUsagePort =
            new RecordingQuotaUsageQueryPort(
                1L
            );

        RecordingAuditRepository auditRepository =
            new RecordingAuditRepository(
                77L
            );

        PublicationSelectionService service =
            service(
                historyPort,
                quotaUsagePort,
                auditRepository
            );

        PublicationSelectionExecution execution =
            service.execute(
                List.of(
                    previouslyPublished,
                    neverPublished
                ),
                CHANNEL,
                DESTINATION,
                NOW
            );

        assertEquals(
            77L,
            execution.auditRunId()
        );

        assertSame(
            execution.result(),
            auditRepository.savedResult
        );

        assertEquals(
            Set.of(
                neverPublished.asin(),
                previouslyPublished.asin()
            ),
            historyPort.requestedAsins
        );

        assertEquals(
            CHANNEL,
            historyPort.channel
        );

        assertEquals(
            DESTINATION,
            historyPort.destination
        );

        assertEquals(
            CHANNEL,
            quotaUsagePort.channel
        );

        assertEquals(
            DESTINATION,
            quotaUsagePort.destination
        );

        assertEquals(
            LocalDate.of(
                2026,
                9,
                27
            ),
            quotaUsagePort.quotaDate
        );

        assertEquals(
            1L,
            execution.result()
                .selectedCount()
        );

        PublicationSelectionDecision selected =
            decisionFor(
                execution.result(),
                neverPublished.dealEvaluationId()
            );

        assertEquals(
            PublicationSelectionDecisionStatus.SELECTED,
            selected.status()
        );

        PublicationSelectionDecision quotaLimited =
            decisionFor(
                execution.result(),
                previouslyPublished.dealEvaluationId()
            );

        assertEquals(
            PublicationSelectionDecisionStatus
                .NOT_SELECTED_DUE_TO_QUOTA,
            quotaLimited.status()
        );
    }

    @Test
    void shouldResolveQuotaDateUsingQuotaProfileZone() {

        Instant instant =
            Instant.parse(
                "2026-09-28T02:30:00Z"
            );

        RecordingQuotaUsageQueryPort quotaUsagePort =
            new RecordingQuotaUsageQueryPort(
                0L
            );

        RecordingAuditRepository auditRepository =
            new RecordingAuditRepository(
                1L
            );

        PublicationSelectionService service =
            service(
                new RecordingHistoryQueryPort(
                    Map.of()
                ),
                quotaUsagePort,
                auditRepository
            );

        PublicationSelectionExecution execution =
            service.execute(
                List.of(),
                CHANNEL,
                DESTINATION,
                instant
            );

        assertEquals(
            LocalDate.of(
                2026,
                9,
                27
            ),
            quotaUsagePort.quotaDate
        );

        assertEquals(
            LocalDate.of(
                2026,
                9,
                27
            ),
            execution.result()
                .quotaSnapshot()
                .quotaDate()
        );
    }

    @Test
    void shouldPersistAuditForEmptyCandidateSet() {

        RecordingHistoryQueryPort historyPort =
            new RecordingHistoryQueryPort(
                Map.of()
            );

        RecordingAuditRepository auditRepository =
            new RecordingAuditRepository(
                10L
            );

        PublicationSelectionService service =
            service(
                historyPort,
                new RecordingQuotaUsageQueryPort(
                    0L
                ),
                auditRepository
            );

        PublicationSelectionExecution execution =
            service.execute(
                List.of(),
                CHANNEL,
                DESTINATION,
                NOW
            );

        assertEquals(
            10L,
            execution.auditRunId()
        );

        assertTrue(
            execution.result()
                .decisions()
                .isEmpty()
        );

        assertEquals(
            0,
            historyPort.callCount
        );

        assertSame(
            execution.result(),
            auditRepository.savedResult
        );
    }

    @Test
    void shouldRejectDuplicateAsinBeforeUsingDependencies() {

        PublicationSelectionService service =
            serviceWithDependenciesThatMustNotBeCalled();

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.execute(
                    List.of(
                        sourceCandidate(
                            1L,
                            "B0SVC18001",
                            "90.0000"
                        ),
                        sourceCandidate(
                            2L,
                            "B0SVC18001",
                            "80.0000"
                        )
                    ),
                    CHANNEL,
                    DESTINATION,
                    NOW
                )
        );
    }

    @Test
    void shouldRejectDuplicateEvaluationBeforeUsingDependencies() {

        PublicationSelectionService service =
            serviceWithDependenciesThatMustNotBeCalled();

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.execute(
                    List.of(
                        sourceCandidate(
                            1L,
                            "B0SVC18001",
                            "90.0000"
                        ),
                        sourceCandidate(
                            1L,
                            "B0SVC18002",
                            "80.0000"
                        )
                    ),
                    CHANNEL,
                    DESTINATION,
                    NOW
                )
        );
    }

    @Test
    void shouldRejectNegativeQuotaUsage() {

        PublicationSelectionService service =
            service(
                new RecordingHistoryQueryPort(
                    Map.of()
                ),
                new RecordingQuotaUsageQueryPort(
                    -1L
                ),
                new RecordingAuditRepository(
                    1L
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.execute(
                    List.of(
                        sourceCandidate(
                            1L,
                            "B0SVC18001",
                            "90.0000"
                        )
                    ),
                    CHANNEL,
                    DESTINATION,
                    NOW
                )
        );
    }

    @Test
    void shouldRejectHistoryForAsinThatWasNotRequested() {

        Asin requestedAsin =
            new Asin(
                "B0SVC18001"
            );

        Asin unexpectedAsin =
            new Asin(
                "B0SVC18002"
            );

        PublicationSelectionService service =
            service(
                new RecordingHistoryQueryPort(
                    Map.of(
                        unexpectedAsin,
                        new SuccessfulPublicationHistory(
                            unexpectedAsin,
                            NOW.minus(
                                Duration.ofDays(
                                    20
                                )
                            ),
                            1L
                        )
                    )
                ),
                new RecordingQuotaUsageQueryPort(
                    0L
                ),
                new RecordingAuditRepository(
                    1L
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.execute(
                    List.of(
                        new PublicationSelectionSourceCandidate(
                            1L,
                            requestedAsin,
                            new BigDecimal(
                                "90.0000"
                            )
                        )
                    ),
                    CHANNEL,
                    DESTINATION,
                    NOW
                )
        );
    }

    @Test
    void shouldRejectHistoryMappedUnderDifferentAsin() {

        Asin requestedAsin =
            new Asin(
                "B0SVC18001"
            );

        Asin historyAsin =
            new Asin(
                "B0SVC18002"
            );

        Map<Asin, SuccessfulPublicationHistory> histories =
            new HashMap<>();

        histories.put(
            requestedAsin,
            new SuccessfulPublicationHistory(
                historyAsin,
                NOW.minus(
                    Duration.ofDays(
                        20
                    )
                ),
                1L
            )
        );

        PublicationSelectionService service =
            service(
                new RecordingHistoryQueryPort(
                    histories
                ),
                new RecordingQuotaUsageQueryPort(
                    0L
                ),
                new RecordingAuditRepository(
                    1L
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.execute(
                    List.of(
                        new PublicationSelectionSourceCandidate(
                            1L,
                            requestedAsin,
                            new BigDecimal(
                                "90.0000"
                            )
                        )
                    ),
                    CHANNEL,
                    DESTINATION,
                    NOW
                )
        );
    }

    @Test
    void shouldRejectNonPositiveAuditRunId() {

        PublicationSelectionService service =
            service(
                new RecordingHistoryQueryPort(
                    Map.of()
                ),
                new RecordingQuotaUsageQueryPort(
                    0L
                ),
                new RecordingAuditRepository(
                    0L
                )
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                service.execute(
                    List.of(),
                    CHANNEL,
                    DESTINATION,
                    NOW
                )
        );
    }

    private PublicationSelectionService service(
        SuccessfulPublicationHistoryQueryPort historyPort,
        PublicationQuotaUsageQueryPort quotaUsagePort,
        PublicationSelectionAuditRepository auditRepository
    ) {

        PublicationSelectionProfileProvider selectionProfileProvider =
            (channel, destination) ->
                SELECTION_PROFILE;

        PublicationQuotaProfileProvider quotaProfileProvider =
            (channel, destination) ->
                QUOTA_PROFILE;

        return new PublicationSelectionService(
            selectionProfileProvider,
            quotaProfileProvider,
            quotaUsagePort,
            historyPort,
            auditRepository,
            new PublicationSelectionPolicy()
        );
    }

    private PublicationSelectionService
    serviceWithDependenciesThatMustNotBeCalled() {

        PublicationSelectionProfileProvider selectionProfileProvider =
            (channel, destination) -> {
                throw new AssertionError(
                    "selection profile provider must not be called"
                );
            };

        PublicationQuotaProfileProvider quotaProfileProvider =
            (channel, destination) -> {
                throw new AssertionError(
                    "quota profile provider must not be called"
                );
            };

        PublicationQuotaUsageQueryPort quotaUsagePort =
            (channel, destination, quotaDate) -> {
                throw new AssertionError(
                    "quota usage query must not be called"
                );
            };

        SuccessfulPublicationHistoryQueryPort historyPort =
            (asins, channel, destination) -> {
                throw new AssertionError(
                    "history query must not be called"
                );
            };

        PublicationSelectionAuditRepository auditRepository =
            result -> {
                throw new AssertionError(
                    "audit repository must not be called"
                );
            };

        return new PublicationSelectionService(
            selectionProfileProvider,
            quotaProfileProvider,
            quotaUsagePort,
            historyPort,
            auditRepository,
            new PublicationSelectionPolicy()
        );
    }

    private PublicationSelectionSourceCandidate sourceCandidate(
        long dealEvaluationId,
        String asin,
        String score
    ) {

        return new PublicationSelectionSourceCandidate(
            dealEvaluationId,
            new Asin(
                asin
            ),
            new BigDecimal(
                score
            )
        );
    }

    private PublicationSelectionDecision decisionFor(
        PublicationSelectionResult result,
        long dealEvaluationId
    ) {

        return result.decisions()
            .stream()
            .filter(
                decision ->
                    decision.candidate()
                        .dealEvaluationId()
                        == dealEvaluationId
            )
            .findFirst()
            .orElseThrow();
    }

    private static final class RecordingHistoryQueryPort
        implements SuccessfulPublicationHistoryQueryPort {

        private final Map<Asin, SuccessfulPublicationHistory>
            histories;

        private int callCount;

        private Set<Asin> requestedAsins;

        private String channel;

        private String destination;

        private RecordingHistoryQueryPort(
            Map<Asin, SuccessfulPublicationHistory> histories
        ) {

            this.histories =
                histories;
        }

        @Override
        public Map<Asin, SuccessfulPublicationHistory>
        findSuccessfulByAsins(
            Set<Asin> asins,
            String channel,
            String destination
        ) {

            callCount++;

            requestedAsins =
                Set.copyOf(
                    asins
                );

            this.channel =
                channel;

            this.destination =
                destination;

            return histories;
        }
    }

    private static final class RecordingQuotaUsageQueryPort
        implements PublicationQuotaUsageQueryPort {

        private final long occupiedSlots;

        private String channel;

        private String destination;

        private LocalDate quotaDate;

        private RecordingQuotaUsageQueryPort(
            long occupiedSlots
        ) {

            this.occupiedSlots =
                occupiedSlots;
        }

        @Override
        public long occupiedSlots(
            String channel,
            String destination,
            LocalDate quotaDate
        ) {

            this.channel =
                channel;

            this.destination =
                destination;

            this.quotaDate =
                quotaDate;

            return occupiedSlots;
        }
    }

    private static final class RecordingAuditRepository
        implements PublicationSelectionAuditRepository {

        private final long auditRunId;

        private PublicationSelectionResult savedResult;

        private RecordingAuditRepository(
            long auditRunId
        ) {

            this.auditRunId =
                auditRunId;
        }

        @Override
        public long save(
            PublicationSelectionResult result
        ) {

            savedResult =
                result;

            return auditRunId;
        }
    }
}
