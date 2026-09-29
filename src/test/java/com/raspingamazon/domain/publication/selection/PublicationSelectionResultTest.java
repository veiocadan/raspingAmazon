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

class PublicationSelectionResultTest {

    private static final Instant NOW =
        Instant.parse(
            "2026-09-27T20:00:00Z"
        );

    private static final PublicationSelectionProfile SELECTION_PROFILE =
        new PublicationSelectionProfile(
            PublicationSelectionPolicy.VERSION,
            Duration.ofDays(
                2
            ),
            Duration.ofDays(
                7
            )
        );

    @Test
    void shouldRepresentCompleteSelectionResult() {

        PublicationSelectionCandidate first =
            candidate(
                1L,
                "B0RES18001",
                "90.0000"
            );

        PublicationSelectionCandidate second =
            candidate(
                2L,
                "B0RES18002",
                "80.0000"
            );

        PublicationSelectionCandidate third =
            candidate(
                3L,
                "B0RES18003",
                "70.0000"
            );

        PublicationSelectionResult result =
            new PublicationSelectionResult(
                NOW,
                SELECTION_PROFILE,
                quotaSnapshot(
                    3,
                    1L
                ),
                List.of(
                    selected(
                        first,
                        1
                    ),
                    selected(
                        second,
                        2
                    ),
                    quotaLimited(
                        third,
                        3
                    )
                )
            );

        assertEquals(
            List.of(
                first,
                second
            ),
            result.selectedCandidates()
        );

        assertEquals(
            2L,
            result.selectedCount()
        );

        assertEquals(
            0L,
            result.deferredByHardCooldownCount()
        );

        assertEquals(
            1L,
            result.notSelectedDueToQuotaCount()
        );
    }

    @Test
    void shouldAllowHardCooldownDecisionOutsideRanking() {

        PublicationSelectionCandidate blocked =
            publishedCandidate(
                1L,
                "B0RES18001"
            );

        PublicationSelectionCandidate selected =
            candidate(
                2L,
                "B0RES18002",
                "80.0000"
            );

        PublicationSelectionResult result =
            new PublicationSelectionResult(
                NOW,
                SELECTION_PROFILE,
                quotaSnapshot(
                    1,
                    0L
                ),
                List.of(
                    new PublicationSelectionDecision(
                        blocked,
                        PublicationSelectionDecisionStatus
                            .DEFERRED_DUE_TO_HARD_COOLDOWN,
                        PublicationSelectionRecency
                            .INSIDE_HARD_COOLDOWN,
                        null
                    ),
                    selected(
                        selected,
                        1
                    )
                )
            );

        assertEquals(
            1L,
            result.selectedCount()
        );

        assertEquals(
            1L,
            result.deferredByHardCooldownCount()
        );
    }

    @Test
    void shouldRequireAllEligiblePositionsToBeContiguous() {

        PublicationSelectionCandidate first =
            candidate(
                1L,
                "B0RES18001",
                "90.0000"
            );

        PublicationSelectionCandidate second =
            candidate(
                2L,
                "B0RES18002",
                "80.0000"
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionResult(
                    NOW,
                    SELECTION_PROFILE,
                    quotaSnapshot(
                        2,
                        0L
                    ),
                    List.of(
                        selected(
                            first,
                            1
                        ),
                        selected(
                            second,
                            3
                        )
                    )
                )
        );
    }

    @Test
    void shouldRejectDuplicatePriorityPosition() {

        PublicationSelectionCandidate first =
            candidate(
                1L,
                "B0RES18001",
                "90.0000"
            );

        PublicationSelectionCandidate second =
            candidate(
                2L,
                "B0RES18002",
                "80.0000"
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionResult(
                    NOW,
                    SELECTION_PROFILE,
                    quotaSnapshot(
                        2,
                        0L
                    ),
                    List.of(
                        selected(
                            first,
                            1
                        ),
                        selected(
                            second,
                            1
                        )
                    )
                )
        );
    }

    @Test
    void shouldRejectDuplicateEvaluation() {

        PublicationSelectionCandidate candidate =
            candidate(
                1L,
                "B0RES18001",
                "90.0000"
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionResult(
                    NOW,
                    SELECTION_PROFILE,
                    quotaSnapshot(
                        2,
                        0L
                    ),
                    List.of(
                        selected(
                            candidate,
                            1
                        ),
                        new PublicationSelectionDecision(
                            candidate,
                            PublicationSelectionDecisionStatus
                                .NOT_SELECTED_DUE_TO_QUOTA,
                            PublicationSelectionRecency
                                .NEVER_SUCCESSFULLY_PUBLISHED,
                            2
                        )
                    )
                )
        );
    }

    @Test
    void shouldRejectTooManySelectedCandidatesForQuota() {

        PublicationSelectionCandidate first =
            candidate(
                1L,
                "B0RES18001",
                "90.0000"
            );

        PublicationSelectionCandidate second =
            candidate(
                2L,
                "B0RES18002",
                "80.0000"
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionResult(
                    NOW,
                    SELECTION_PROFILE,
                    quotaSnapshot(
                        1,
                        0L
                    ),
                    List.of(
                        selected(
                            first,
                            1
                        ),
                        selected(
                            second,
                            2
                        )
                    )
                )
        );
    }

    @Test
    void shouldRequireSelectedStatusInsideAvailablePriorityRange() {

        PublicationSelectionCandidate first =
            candidate(
                1L,
                "B0RES18001",
                "90.0000"
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionResult(
                    NOW,
                    SELECTION_PROFILE,
                    quotaSnapshot(
                        1,
                        0L
                    ),
                    List.of(
                        quotaLimited(
                            first,
                            1
                        )
                    )
                )
        );
    }

    @Test
    void shouldRejectCandidateFromDifferentQuotaScope() {

        PublicationSelectionCandidate candidate =
            new PublicationSelectionCandidate(
                1L,
                new Asin(
                    "B0RES18001"
                ),
                new BigDecimal(
                    "90.0000"
                ),
                "WHATSAPP",
                "@phase18",
                null
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionResult(
                    NOW,
                    SELECTION_PROFILE,
                    quotaSnapshot(
                        1,
                        0L
                    ),
                    List.of(
                        selected(
                            candidate,
                            1
                        )
                    )
                )
        );
    }

    private PublicationSelectionDecision selected(
        PublicationSelectionCandidate candidate,
        int position
    ) {

        return new PublicationSelectionDecision(
            candidate,
            PublicationSelectionDecisionStatus.SELECTED,
            PublicationSelectionRecency
                .NEVER_SUCCESSFULLY_PUBLISHED,
            position
        );
    }

    private PublicationSelectionDecision quotaLimited(
        PublicationSelectionCandidate candidate,
        int position
    ) {

        return new PublicationSelectionDecision(
            candidate,
            PublicationSelectionDecisionStatus
                .NOT_SELECTED_DUE_TO_QUOTA,
            PublicationSelectionRecency
                .NEVER_SUCCESSFULLY_PUBLISHED,
            position
        );
    }

    private PublicationSelectionCandidate candidate(
        long evaluationId,
        String asin,
        String score
    ) {

        return new PublicationSelectionCandidate(
            evaluationId,
            new Asin(
                asin
            ),
            new BigDecimal(
                score
            ),
            "TELEGRAM",
            "@phase18",
            null
        );
    }

    private PublicationSelectionCandidate publishedCandidate(
        long evaluationId,
        String asinValue
    ) {

        Asin asin =
            new Asin(
                asinValue
            );

        return new PublicationSelectionCandidate(
            evaluationId,
            asin,
            new BigDecimal(
                "80.0000"
            ),
            "TELEGRAM",
            "@phase18",
            new SuccessfulPublicationHistory(
                asin,
                NOW.minus(
                    Duration.ofDays(
                        1
                    )
                ),
                1L
            )
        );
    }

    private PublicationQuotaSnapshot quotaSnapshot(
        int max,
        long occupied
    ) {

        return new PublicationQuotaSnapshot(
            "TELEGRAM",
            "@phase18",
            LocalDate.of(
                2026,
                9,
                27
            ),
            "PUBLICATION_QUOTA_V1",
            max,
            occupied
        );
    }
}
