package com.raspingamazon.domain.publication.selection;

import com.raspingamazon.domain.product.Asin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationSelectionPolicyTest {

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
    void shouldPrioritizeNeverPublishedBeforePreviouslyPublished() {

        PublicationSelectionCandidate publishedHighScore =
            publishedCandidate(
                1L,
                "B0SEL18001",
                "99.0000",
                NOW.minus(
                    Duration.ofDays(
                        10
                    )
                ),
                1L
            );

        PublicationSelectionCandidate neverPublishedLowerScore =
            neverPublishedCandidate(
                2L,
                "B0SEL18002",
                "70.0000"
            );

        List<PublicationSelectionCandidate> result =
            prioritize(
                List.of(
                    publishedHighScore,
                    neverPublishedLowerScore
                )
            );

        assertEquals(
            List.of(
                neverPublishedLowerScore,
                publishedHighScore
            ),
            result
        );
    }

    @Test
    void shouldOrderNeverPublishedByScoreDescending() {

        PublicationSelectionCandidate lower =
            neverPublishedCandidate(
                1L,
                "B0SEL18001",
                "70.0000"
            );

        PublicationSelectionCandidate higher =
            neverPublishedCandidate(
                2L,
                "B0SEL18002",
                "95.0000"
            );

        PublicationSelectionCandidate middle =
            neverPublishedCandidate(
                3L,
                "B0SEL18003",
                "80.0000"
            );

        List<PublicationSelectionCandidate> result =
            prioritize(
                List.of(
                    lower,
                    higher,
                    middle
                )
            );

        assertEquals(
            List.of(
                higher,
                middle,
                lower
            ),
            result
        );
    }

    @Test
    void shouldOrderPublishedCandidatesByOldestSuccessfulPublication() {

        PublicationSelectionCandidate recent =
            publishedCandidate(
                1L,
                "B0SEL18001",
                "99.0000",
                NOW.minus(
                    Duration.ofDays(
                        8
                    )
                ),
                5L
            );

        PublicationSelectionCandidate oldest =
            publishedCandidate(
                2L,
                "B0SEL18002",
                "60.0000",
                NOW.minus(
                    Duration.ofDays(
                        20
                    )
                ),
                2L
            );

        PublicationSelectionCandidate middle =
            publishedCandidate(
                3L,
                "B0SEL18003",
                "80.0000",
                NOW.minus(
                    Duration.ofDays(
                        12
                    )
                ),
                3L
            );

        List<PublicationSelectionCandidate> result =
            prioritize(
                List.of(
                    recent,
                    oldest,
                    middle
                )
            );

        assertEquals(
            List.of(
                oldest,
                middle,
                recent
            ),
            result
        );
    }

    @Test
    void shouldUseScoreWhenSuccessfulPublicationTimeIsEqual() {

        Instant sameHistoryTime =
            NOW.minus(
                Duration.ofDays(
                    10
                )
            );

        PublicationSelectionCandidate lower =
            publishedCandidate(
                1L,
                "B0SEL18001",
                "70.0000",
                sameHistoryTime,
                1L
            );

        PublicationSelectionCandidate higher =
            publishedCandidate(
                2L,
                "B0SEL18002",
                "90.0000",
                sameHistoryTime,
                1L
            );

        List<PublicationSelectionCandidate> result =
            prioritize(
                List.of(
                    lower,
                    higher
                )
            );

        assertEquals(
            List.of(
                higher,
                lower
            ),
            result
        );
    }

    @Test
    void shouldUseAsinAsDeterministicTieBreaker() {

        PublicationSelectionCandidate second =
            neverPublishedCandidate(
                1L,
                "B0SEL18002",
                "80.0000"
            );

        PublicationSelectionCandidate first =
            neverPublishedCandidate(
                2L,
                "B0SEL18001",
                "80.0000"
            );

        List<PublicationSelectionCandidate> result =
            prioritize(
                List.of(
                    second,
                    first
                )
            );

        assertEquals(
            List.of(
                first,
                second
            ),
            result
        );
    }

    @Test
    void shouldUseDealEvaluationIdAsFinalTieBreaker() {

        PublicationSelectionCandidate laterEvaluation =
            neverPublishedCandidate(
                20L,
                "B0SEL18001",
                "80.0000"
            );

        PublicationSelectionCandidate earlierEvaluation =
            neverPublishedCandidate(
                10L,
                "B0SEL18001",
                "80.0000"
            );

        List<PublicationSelectionCandidate> result =
            prioritize(
                List.of(
                    laterEvaluation,
                    earlierEvaluation
                )
            );

        assertEquals(
            List.of(
                earlierEvaluation,
                laterEvaluation
            ),
            result
        );
    }

    @Test
    void shouldExcludeCandidateInsideHardCooldown() {

        PublicationSelectionCandidate blocked =
            publishedCandidate(
                1L,
                "B0SEL18001",
                "99.0000",
                NOW.minus(
                    Duration.ofDays(
                        1
                    )
                ),
                1L
            );

        PublicationSelectionCandidate available =
            neverPublishedCandidate(
                2L,
                "B0SEL18002",
                "50.0000"
            );

        List<PublicationSelectionCandidate> result =
            prioritize(
                List.of(
                    blocked,
                    available
                )
            );

        assertEquals(
            List.of(
                available
            ),
            result
        );
    }

    @Test
    void shouldAllowCandidateExactlyAtHardCooldownBoundary() {

        PublicationSelectionCandidate candidate =
            publishedCandidate(
                1L,
                "B0SEL18001",
                "80.0000",
                NOW.minus(
                    PROFILE.hardCooldown()
                ),
                1L
            );

        List<PublicationSelectionCandidate> result =
            prioritize(
                List.of(
                    candidate
                )
            );

        assertEquals(
            List.of(
                candidate
            ),
            result
        );
    }

    @Test
    void shouldPrioritizeNormalCandidateBeforePreferredCooldownCandidate() {

        PublicationSelectionCandidate preferredHighScore =
            publishedCandidate(
                1L,
                "B0SEL18001",
                "99.0000",
                NOW.minus(
                    Duration.ofDays(
                        4
                    )
                ),
                1L
            );

        PublicationSelectionCandidate normalLowerScore =
            publishedCandidate(
                2L,
                "B0SEL18002",
                "60.0000",
                NOW.minus(
                    Duration.ofDays(
                        10
                    )
                ),
                1L
            );

        List<PublicationSelectionCandidate> result =
            prioritize(
                List.of(
                    preferredHighScore,
                    normalLowerScore
                )
            );

        assertEquals(
            List.of(
                normalLowerScore,
                preferredHighScore
            ),
            result
        );
    }

    @Test
    void shouldTreatPreferredCooldownBoundaryAsNormalPriority() {

        PublicationSelectionCandidate exactBoundary =
            publishedCandidate(
                1L,
                "B0SEL18001",
                "60.0000",
                NOW.minus(
                    PROFILE.preferredCooldown()
                ),
                1L
            );

        PublicationSelectionCandidate stillPreferred =
            publishedCandidate(
                2L,
                "B0SEL18002",
                "99.0000",
                NOW.minus(
                    Duration.ofDays(
                        6
                    )
                ),
                1L
            );

        List<PublicationSelectionCandidate> result =
            prioritize(
                List.of(
                    stillPreferred,
                    exactBoundary
                )
            );

        assertEquals(
            List.of(
                exactBoundary,
                stillPreferred
            ),
            result
        );
    }

    @Test
    void shouldDisableCooldownWhenBothDurationsAreZero() {

        PublicationSelectionProfile disabledCooldown =
            new PublicationSelectionProfile(
                PublicationSelectionPolicy.VERSION,
                Duration.ZERO,
                Duration.ZERO
            );

        PublicationSelectionCandidate candidate =
            publishedCandidate(
                1L,
                "B0SEL18001",
                "80.0000",
                NOW,
                1L
            );

        List<PublicationSelectionCandidate> result =
            policy.prioritize(
                List.of(
                    candidate
                ),
                disabledCooldown,
                NOW
            );

        assertEquals(
            List.of(
                candidate
            ),
            result
        );
    }

    @Test
    void shouldNotModifyInputList() {

        PublicationSelectionCandidate lower =
            neverPublishedCandidate(
                1L,
                "B0SEL18001",
                "70.0000"
            );

        PublicationSelectionCandidate higher =
            neverPublishedCandidate(
                2L,
                "B0SEL18002",
                "90.0000"
            );

        List<PublicationSelectionCandidate> input =
            new ArrayList<>(
                List.of(
                    lower,
                    higher
                )
            );

        List<PublicationSelectionCandidate> result =
            prioritize(
                input
            );

        assertEquals(
            List.of(
                lower,
                higher
            ),
            input
        );

        assertEquals(
            List.of(
                higher,
                lower
            ),
            result
        );
    }

    @Test
    void shouldReturnEmptyListForEmptyInput() {

        List<PublicationSelectionCandidate> result =
            prioritize(
                List.of()
            );

        assertTrue(
            result.isEmpty()
        );
    }

    @Test
    void shouldRejectMixedChannelScope() {

        PublicationSelectionCandidate telegram =
            neverPublishedCandidate(
                1L,
                "B0SEL18001",
                "80.0000"
            );

        PublicationSelectionCandidate whatsapp =
            new PublicationSelectionCandidate(
                2L,
                new Asin(
                    "B0SEL18002"
                ),
                new BigDecimal(
                    "80.0000"
                ),
                "WHATSAPP",
                DESTINATION,
                null
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                prioritize(
                    List.of(
                        telegram,
                        whatsapp
                    )
                )
        );
    }

    @Test
    void shouldRejectMixedDestinationScope() {

        PublicationSelectionCandidate first =
            neverPublishedCandidate(
                1L,
                "B0SEL18001",
                "80.0000"
            );

        PublicationSelectionCandidate otherDestination =
            new PublicationSelectionCandidate(
                2L,
                new Asin(
                    "B0SEL18002"
                ),
                new BigDecimal(
                    "80.0000"
                ),
                CHANNEL,
                "@another_destination",
                null
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                prioritize(
                    List.of(
                        first,
                        otherDestination
                    )
                )
        );
    }

    @Test
    void shouldRejectProfileFromDifferentPolicyVersion() {

        PublicationSelectionProfile differentVersion =
            new PublicationSelectionProfile(
                "PUBLICATION_SELECTION_V2",
                Duration.ZERO,
                Duration.ZERO
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                policy.prioritize(
                    List.of(),
                    differentVersion,
                    NOW
                )
        );
    }

    @Test
    void shouldRejectFutureSuccessfulPublicationHistory() {

        PublicationSelectionCandidate candidate =
            publishedCandidate(
                1L,
                "B0SEL18001",
                "80.0000",
                NOW.plusSeconds(
                    1L
                ),
                1L
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                prioritize(
                    List.of(
                        candidate
                    )
                )
        );
    }

    @Test
    void shouldRejectNullCandidateList() {

        assertThrows(
            NullPointerException.class,
            () ->
                policy.prioritize(
                    null,
                    PROFILE,
                    NOW
                )
        );
    }

    @Test
    void shouldRejectCandidateListContainingNull() {

        List<PublicationSelectionCandidate> candidates =
            new ArrayList<>();

        candidates.add(
            neverPublishedCandidate(
                1L,
                "B0SEL18001",
                "80.0000"
            )
        );

        candidates.add(
            null
        );

        assertThrows(
            NullPointerException.class,
            () ->
                prioritize(
                    candidates
                )
        );
    }

    @Test
    void shouldRejectNullProfile() {

        assertThrows(
            NullPointerException.class,
            () ->
                policy.prioritize(
                    List.of(),
                    null,
                    NOW
                )
        );
    }

    @Test
    void shouldRejectNullCurrentTime() {

        assertThrows(
            NullPointerException.class,
            () ->
                policy.prioritize(
                    List.of(),
                    PROFILE,
                    null
                )
        );
    }

    @Test
    void shouldExposePolicyVersion() {

        assertEquals(
            "PUBLICATION_SELECTION_V1",
            PublicationSelectionPolicy.VERSION
        );
    }

    @Test
    void candidateShouldRejectHistoryFromDifferentAsin() {

        SuccessfulPublicationHistory wrongHistory =
            new SuccessfulPublicationHistory(
                new Asin(
                    "B0SEL18002"
                ),
                NOW.minus(
                    Duration.ofDays(
                        10
                    )
                ),
                1L
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionCandidate(
                    1L,
                    new Asin(
                        "B0SEL18001"
                    ),
                    new BigDecimal(
                        "80.0000"
                    ),
                    CHANNEL,
                    DESTINATION,
                    wrongHistory
                )
        );
    }

    private List<PublicationSelectionCandidate> prioritize(
        List<PublicationSelectionCandidate> candidates
    ) {

        return policy.prioritize(
            candidates,
            PROFILE,
            NOW
        );
    }

    private PublicationSelectionCandidate neverPublishedCandidate(
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

    private PublicationSelectionCandidate publishedCandidate(
        long dealEvaluationId,
        String asin,
        String score,
        Instant lastSuccessfulPublicationAt,
        long successfulPublicationCount
    ) {

        Asin candidateAsin =
            new Asin(
                asin
            );

        return new PublicationSelectionCandidate(
            dealEvaluationId,
            candidateAsin,
            new BigDecimal(
                score
            ),
            CHANNEL,
            DESTINATION,
            new SuccessfulPublicationHistory(
                candidateAsin,
                lastSuccessfulPublicationAt,
                successfulPublicationCount
            )
        );
    }
}
