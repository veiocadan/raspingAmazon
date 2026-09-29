package com.raspingamazon.domain.publication.selection;

import com.raspingamazon.domain.product.Asin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationSelectionPolicyDecisionTest {

    private static final String CHANNEL =
        "TELEGRAM";

    private static final String DESTINATION =
        "@phase18";

    private static final Instant NOW =
        Instant.parse(
            "2026-09-27T20:00:00Z"
        );

    private static final PublicationSelectionProfile PROFILE =
        new PublicationSelectionProfile(
            PublicationSelectionPolicy.VERSION,
            Duration.ofDays(
                2
            ),
            Duration.ofDays(
                7
            )
        );

    private final PublicationSelectionPolicy policy =
        new PublicationSelectionPolicy();

    @Test
    void shouldSelectCandidatesAccordingToPriorityAndAvailableQuota() {

        PublicationSelectionCandidate lowerNeverPublished =
            neverPublished(
                1L,
                "B0POL18001",
                "70.0000"
            );

        PublicationSelectionCandidate published =
            published(
                2L,
                "B0POL18002",
                "99.0000",
                NOW.minus(
                    Duration.ofDays(
                        20
                    )
                )
            );

        PublicationSelectionCandidate higherNeverPublished =
            neverPublished(
                3L,
                "B0POL18003",
                "90.0000"
            );

        PublicationSelectionResult result =
            policy.select(
                List.of(
                    published,
                    lowerNeverPublished,
                    higherNeverPublished
                ),
                PROFILE,
                quotaSnapshot(
                    3,
                    1L
                ),
                NOW
            );

        assertEquals(
            List.of(
                higherNeverPublished,
                lowerNeverPublished
            ),
            result.selectedCandidates()
        );

        assertEquals(
            2L,
            result.selectedCount()
        );

        PublicationSelectionDecision publishedDecision =
            decisionFor(
                result,
                published.dealEvaluationId()
            );

        assertEquals(
            PublicationSelectionDecisionStatus
                .NOT_SELECTED_DUE_TO_QUOTA,
            publishedDecision.status()
        );

        assertEquals(
            3,
            publishedDecision
                .priorityPositionValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldDeferHardCooldownWithoutConsumingQuota() {

        PublicationSelectionCandidate blocked =
            published(
                1L,
                "B0POL18001",
                "99.0000",
                NOW.minus(
                    Duration.ofDays(
                        1
                    )
                )
            );

        PublicationSelectionCandidate eligible =
            neverPublished(
                2L,
                "B0POL18002",
                "50.0000"
            );

        PublicationSelectionResult result =
            policy.select(
                List.of(
                    blocked,
                    eligible
                ),
                PROFILE,
                quotaSnapshot(
                    1,
                    0L
                ),
                NOW
            );

        assertEquals(
            List.of(
                eligible
            ),
            result.selectedCandidates()
        );

        assertEquals(
            1L,
            result.deferredByHardCooldownCount()
        );

        PublicationSelectionDecision blockedDecision =
            decisionFor(
                result,
                blocked.dealEvaluationId()
            );

        assertEquals(
            PublicationSelectionDecisionStatus
                .DEFERRED_DUE_TO_HARD_COOLDOWN,
            blockedDecision.status()
        );

        assertEquals(
            PublicationSelectionRecency
                .INSIDE_HARD_COOLDOWN,
            blockedDecision.recency()
        );

        assertTrue(
            blockedDecision
                .priorityPositionValue()
                .isEmpty()
        );
    }

    @Test
    void shouldClassifyPreferredCooldownCandidate() {

        PublicationSelectionCandidate candidate =
            published(
                1L,
                "B0POL18001",
                "80.0000",
                NOW.minus(
                    Duration.ofDays(
                        4
                    )
                )
            );

        PublicationSelectionResult result =
            policy.select(
                List.of(
                    candidate
                ),
                PROFILE,
                quotaSnapshot(
                    1,
                    0L
                ),
                NOW
            );

        PublicationSelectionDecision decision =
            decisionFor(
                result,
                candidate.dealEvaluationId()
            );

        assertEquals(
            PublicationSelectionDecisionStatus.SELECTED,
            decision.status()
        );

        assertEquals(
            PublicationSelectionRecency
                .INSIDE_PREFERRED_COOLDOWN,
            decision.recency()
        );
    }

    @Test
    void shouldClassifyCandidateOutsidePreferredCooldown() {

        PublicationSelectionCandidate candidate =
            published(
                1L,
                "B0POL18001",
                "80.0000",
                NOW.minus(
                    Duration.ofDays(
                        10
                    )
                )
            );

        PublicationSelectionResult result =
            policy.select(
                List.of(
                    candidate
                ),
                PROFILE,
                quotaSnapshot(
                    1,
                    0L
                ),
                NOW
            );

        PublicationSelectionDecision decision =
            decisionFor(
                result,
                candidate.dealEvaluationId()
            );

        assertEquals(
            PublicationSelectionRecency
                .OUTSIDE_PREFERRED_COOLDOWN,
            decision.recency()
        );
    }

    @Test
    void shouldMarkAllEligibleCandidatesAsQuotaLimitedWhenQuotaIsExhausted() {

        PublicationSelectionCandidate first =
            neverPublished(
                1L,
                "B0POL18001",
                "90.0000"
            );

        PublicationSelectionCandidate second =
            neverPublished(
                2L,
                "B0POL18002",
                "80.0000"
            );

        PublicationSelectionResult result =
            policy.select(
                List.of(
                    first,
                    second
                ),
                PROFILE,
                quotaSnapshot(
                    2,
                    2L
                ),
                NOW
            );

        assertEquals(
            0L,
            result.selectedCount()
        );

        assertEquals(
            2L,
            result.notSelectedDueToQuotaCount()
        );

        assertTrue(
            result.selectedCandidates()
                .isEmpty()
        );
    }

    @Test
    void shouldReturnEmptyDecisionSetForEmptyCandidateList() {

        PublicationSelectionResult result =
            policy.select(
                List.of(),
                PROFILE,
                quotaSnapshot(
                    7,
                    0L
                ),
                NOW
            );

        assertTrue(
            result.decisions()
                .isEmpty()
        );

        assertEquals(
            0L,
            result.selectedCount()
        );
    }

    @Test
    void shouldRejectQuotaSnapshotFromDifferentChannel() {

        PublicationSelectionCandidate candidate =
            neverPublished(
                1L,
                "B0POL18001",
                "90.0000"
            );

        PublicationQuotaSnapshot differentScope =
            new PublicationQuotaSnapshot(
                "WHATSAPP",
                DESTINATION,
                LocalDate.of(
                    2026,
                    9,
                    27
                ),
                "PUBLICATION_QUOTA_V1",
                7,
                0L
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                policy.select(
                    List.of(
                        candidate
                    ),
                    PROFILE,
                    differentScope,
                    NOW
                )
        );
    }

    @Test
    void shouldRejectNullQuotaSnapshot() {

        assertThrows(
            NullPointerException.class,
            () ->
                policy.select(
                    List.of(),
                    PROFILE,
                    null,
                    NOW
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

    private PublicationSelectionCandidate neverPublished(
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
            CHANNEL,
            DESTINATION,
            null
        );
    }

    private PublicationSelectionCandidate published(
        long dealEvaluationId,
        String asinValue,
        String score,
        Instant lastSuccessfulPublicationAt
    ) {

        Asin asin =
            new Asin(
                asinValue
            );

        return new PublicationSelectionCandidate(
            dealEvaluationId,
            asin,
            new BigDecimal(
                score
            ),
            CHANNEL,
            DESTINATION,
            new SuccessfulPublicationHistory(
                asin,
                lastSuccessfulPublicationAt,
                1L
            )
        );
    }

    private PublicationQuotaSnapshot quotaSnapshot(
        int maximum,
        long occupied
    ) {

        return new PublicationQuotaSnapshot(
            CHANNEL,
            DESTINATION,
            LocalDate.of(
                2026,
                9,
                27
            ),
            "PUBLICATION_QUOTA_V1",
            maximum,
            occupied
        );
    }
}
