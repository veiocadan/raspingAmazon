package com.raspingamazon.domain.publication.selection;

import com.raspingamazon.domain.product.Asin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationSelectionDecisionTest {

    private static final Instant LAST_SUCCESS =
        Instant.parse(
            "2026-09-20T12:00:00Z"
        );

    @Test
    void shouldCreateSelectedNeverPublishedDecision() {

        PublicationSelectionDecision decision =
            new PublicationSelectionDecision(
                neverPublishedCandidate(),
                PublicationSelectionDecisionStatus.SELECTED,
                PublicationSelectionRecency
                    .NEVER_SUCCESSFULLY_PUBLISHED,
                1
            );

        assertTrue(
            decision.selected()
        );

        assertFalse(
            decision.deferredByHardCooldown()
        );

        assertEquals(
            1,
            decision.priorityPositionValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldCreateHardCooldownDeferredDecisionWithoutPosition() {

        PublicationSelectionDecision decision =
            new PublicationSelectionDecision(
                publishedCandidate(),
                PublicationSelectionDecisionStatus
                    .DEFERRED_DUE_TO_HARD_COOLDOWN,
                PublicationSelectionRecency
                    .INSIDE_HARD_COOLDOWN,
                null
            );

        assertTrue(
            decision.deferredByHardCooldown()
        );

        assertTrue(
            decision.priorityPositionValue()
                .isEmpty()
        );
    }

    @Test
    void shouldCreateQuotaLimitedDecision() {

        PublicationSelectionDecision decision =
            new PublicationSelectionDecision(
                publishedCandidate(),
                PublicationSelectionDecisionStatus
                    .NOT_SELECTED_DUE_TO_QUOTA,
                PublicationSelectionRecency
                    .OUTSIDE_PREFERRED_COOLDOWN,
                3
            );

        assertTrue(
            decision.notSelectedDueToQuota()
        );
    }

    @Test
    void shouldRejectNeverPublishedRecencyWithHistory() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionDecision(
                    publishedCandidate(),
                    PublicationSelectionDecisionStatus.SELECTED,
                    PublicationSelectionRecency
                        .NEVER_SUCCESSFULLY_PUBLISHED,
                    1
                )
        );
    }

    @Test
    void shouldRejectHistoricalRecencyWithoutHistory() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionDecision(
                    neverPublishedCandidate(),
                    PublicationSelectionDecisionStatus.SELECTED,
                    PublicationSelectionRecency
                        .OUTSIDE_PREFERRED_COOLDOWN,
                    1
                )
        );
    }

    @Test
    void shouldRejectSelectedCandidateInsideHardCooldown() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionDecision(
                    publishedCandidate(),
                    PublicationSelectionDecisionStatus.SELECTED,
                    PublicationSelectionRecency
                        .INSIDE_HARD_COOLDOWN,
                    null
                )
        );
    }

    @Test
    void shouldRejectPositionForHardCooldownCandidate() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionDecision(
                    publishedCandidate(),
                    PublicationSelectionDecisionStatus
                        .DEFERRED_DUE_TO_HARD_COOLDOWN,
                    PublicationSelectionRecency
                        .INSIDE_HARD_COOLDOWN,
                    1
                )
        );
    }

    @Test
    void shouldRejectEligibleCandidateWithoutPosition() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionDecision(
                    neverPublishedCandidate(),
                    PublicationSelectionDecisionStatus.SELECTED,
                    PublicationSelectionRecency
                        .NEVER_SUCCESSFULLY_PUBLISHED,
                    null
                )
        );
    }

    private PublicationSelectionCandidate neverPublishedCandidate() {

        return new PublicationSelectionCandidate(
            1L,
            new Asin(
                "B0DEC18001"
            ),
            new BigDecimal(
                "90.0000"
            ),
            "TELEGRAM",
            "@phase18",
            null
        );
    }

    private PublicationSelectionCandidate publishedCandidate() {

        Asin asin =
            new Asin(
                "B0DEC18002"
            );

        return new PublicationSelectionCandidate(
            2L,
            asin,
            new BigDecimal(
                "80.0000"
            ),
            "TELEGRAM",
            "@phase18",
            new SuccessfulPublicationHistory(
                asin,
                LAST_SUCCESS,
                1L
            )
        );
    }
}
