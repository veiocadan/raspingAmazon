package com.raspingamazon.domain.publication.selection;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resultado completo e auditável de uma execução da política
 * operacional de seleção.
 *
 * <p>O resultado preserva:</p>
 *
 * <ul>
 *     <li>instante lógico da decisão;</li>
 *     <li>perfil temporal utilizado;</li>
 *     <li>snapshot de quota observado;</li>
 *     <li>uma decisão para cada candidato avaliado.</li>
 * </ul>
 *
 * <p>O objeto representa seleção, não reserva definitiva de quota.
 * A outbox deverá revalidar capacidade dentro da transação de
 * persistência.</p>
 */
public record PublicationSelectionResult(
    Instant decidedAt,
    PublicationSelectionProfile selectionProfile,
    PublicationQuotaSnapshot quotaSnapshot,
    List<PublicationSelectionDecision> decisions
) {

    public PublicationSelectionResult {

        Objects.requireNonNull(
            decidedAt,
            "decidedAt must not be null"
        );

        Objects.requireNonNull(
            selectionProfile,
            "selectionProfile must not be null"
        );

        Objects.requireNonNull(
            quotaSnapshot,
            "quotaSnapshot must not be null"
        );

        Objects.requireNonNull(
            decisions,
            "decisions must not be null"
        );

        decisions =
            List.copyOf(
                decisions
            );

        validateDecisions(
            quotaSnapshot,
            decisions
        );
    }

    /**
     * Candidatos escolhidos pela execução da seleção,
     * preservando a prioridade operacional.
     */
    public List<PublicationSelectionCandidate> selectedCandidates() {

        return decisions.stream()
            .filter(
                PublicationSelectionDecision::selected
            )
            .sorted(
                java.util.Comparator.comparingInt(
                    decision ->
                        decision.priorityPositionValue()
                            .orElseThrow()
                )
            )
            .map(
                PublicationSelectionDecision::candidate
            )
            .toList();
    }

    public long selectedCount() {

        return decisions.stream()
            .filter(
                PublicationSelectionDecision::selected
            )
            .count();
    }

    public long deferredByHardCooldownCount() {

        return decisions.stream()
            .filter(
                PublicationSelectionDecision
                    ::deferredByHardCooldown
            )
            .count();
    }

    public long notSelectedDueToQuotaCount() {

        return decisions.stream()
            .filter(
                PublicationSelectionDecision
                    ::notSelectedDueToQuota
            )
            .count();
    }

    private static void validateDecisions(
        PublicationQuotaSnapshot quotaSnapshot,
        List<PublicationSelectionDecision> decisions
    ) {

        Set<Long> evaluationIds =
            new HashSet<>();

        Set<Integer> positions =
            new HashSet<>();

        int rankedCandidateCount =
            0;

        for (PublicationSelectionDecision decision : decisions) {

            Objects.requireNonNull(
                decision,
                "decisions must not contain null"
            );

            PublicationSelectionCandidate candidate =
                decision.candidate();

            if (!quotaSnapshot.channel()
                .equals(
                    candidate.channel()
                )
                || !quotaSnapshot.destination()
                .equals(
                    candidate.destination()
                )) {

                throw new IllegalArgumentException(
                    "all decisions must belong to quota snapshot scope"
                );
            }

            if (!evaluationIds.add(
                candidate.dealEvaluationId()
            )) {

                throw new IllegalArgumentException(
                    "duplicate dealEvaluationId in selection result"
                );
            }

            if (decision.priorityPosition() != null) {

                rankedCandidateCount++;

                if (!positions.add(
                    decision.priorityPosition()
                )) {

                    throw new IllegalArgumentException(
                        "duplicate priorityPosition "
                            + "in selection result"
                    );
                }
            }
        }

        validateContiguousPositions(
            positions,
            rankedCandidateCount
        );

        int expectedSelectedCount =
            Math.min(
                quotaSnapshot.availableSlots(),
                rankedCandidateCount
            );

        long actualSelectedCount =
            decisions.stream()
                .filter(
                    PublicationSelectionDecision::selected
                )
                .count();

        if (actualSelectedCount
            != expectedSelectedCount) {

            throw new IllegalArgumentException(
                "selected decision count must match "
                    + "available quota and eligible candidates"
            );
        }

        for (PublicationSelectionDecision decision : decisions) {

            if (decision.priorityPosition() == null) {
                continue;
            }

            if (decision.priorityPosition()
                <= expectedSelectedCount) {

                if (!decision.selected()) {

                    throw new IllegalArgumentException(
                        "candidate inside selected priority range "
                            + "must be SELECTED"
                    );
                }

            } else if (!decision.notSelectedDueToQuota()) {

                throw new IllegalArgumentException(
                    "eligible candidate outside selected priority "
                        + "range must be NOT_SELECTED_DUE_TO_QUOTA"
                );
            }
        }
    }

    private static void validateContiguousPositions(
        Set<Integer> positions,
        int rankedCandidateCount
    ) {

        for (int expectedPosition = 1;
             expectedPosition <= rankedCandidateCount;
             expectedPosition++) {

            if (!positions.contains(
                expectedPosition
            )) {

                throw new IllegalArgumentException(
                    "priority positions must be contiguous "
                        + "starting at 1"
                );
            }
        }
    }
}
