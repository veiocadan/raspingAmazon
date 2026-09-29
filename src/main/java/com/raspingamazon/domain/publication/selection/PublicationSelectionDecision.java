package com.raspingamazon.domain.publication.selection;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Decisão auditável tomada para um candidato durante uma execução
 * da seleção operacional.
 *
 * <p>priorityPosition representa a posição entre candidatos que
 * estavam habilitados a participar da ordenação.</p>
 *
 * <p>Candidatos dentro do hard cooldown não possuem posição,
 * porque não participam da ordenação operacional daquele ciclo.</p>
 */
public record PublicationSelectionDecision(
    PublicationSelectionCandidate candidate,
    PublicationSelectionDecisionStatus status,
    PublicationSelectionRecency recency,
    Integer priorityPosition
) {

    public PublicationSelectionDecision {

        Objects.requireNonNull(
            candidate,
            "candidate must not be null"
        );

        Objects.requireNonNull(
            status,
            "status must not be null"
        );

        Objects.requireNonNull(
            recency,
            "recency must not be null"
        );

        if (priorityPosition != null
            && priorityPosition <= 0) {

            throw new IllegalArgumentException(
                "priorityPosition must be positive when present"
            );
        }

        validateHistoryConsistency(
            candidate,
            recency
        );

        validateDecisionConsistency(
            status,
            recency,
            priorityPosition
        );
    }

    public boolean selected() {

        return status
            == PublicationSelectionDecisionStatus.SELECTED;
    }

    public boolean deferredByHardCooldown() {

        return status
            == PublicationSelectionDecisionStatus
            .DEFERRED_DUE_TO_HARD_COOLDOWN;
    }

    public boolean notSelectedDueToQuota() {

        return status
            == PublicationSelectionDecisionStatus
            .NOT_SELECTED_DUE_TO_QUOTA;
    }

    public OptionalInt priorityPositionValue() {

        if (priorityPosition == null) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(
            priorityPosition
        );
    }

    private static void validateHistoryConsistency(
        PublicationSelectionCandidate candidate,
        PublicationSelectionRecency recency
    ) {

        if (recency
            == PublicationSelectionRecency
            .NEVER_SUCCESSFULLY_PUBLISHED) {

            if (!candidate.neverSuccessfullyPublished()) {

                throw new IllegalArgumentException(
                    "NEVER_SUCCESSFULLY_PUBLISHED requires "
                        + "candidate without successful history"
                );
            }

            return;
        }

        if (candidate.neverSuccessfullyPublished()) {

            throw new IllegalArgumentException(
                "publication recency with previous success "
                    + "requires successful publication history"
            );
        }
    }

    private static void validateDecisionConsistency(
        PublicationSelectionDecisionStatus status,
        PublicationSelectionRecency recency,
        Integer priorityPosition
    ) {

        boolean insideHardCooldown =
            recency
                == PublicationSelectionRecency
                .INSIDE_HARD_COOLDOWN;

        if (insideHardCooldown) {

            if (status
                != PublicationSelectionDecisionStatus
                .DEFERRED_DUE_TO_HARD_COOLDOWN) {

                throw new IllegalArgumentException(
                    "candidate inside hard cooldown must be deferred"
                );
            }

            if (priorityPosition != null) {

                throw new IllegalArgumentException(
                    "candidate inside hard cooldown "
                        + "must not have priorityPosition"
                );
            }

            return;
        }

        if (status
            == PublicationSelectionDecisionStatus
            .DEFERRED_DUE_TO_HARD_COOLDOWN) {

            throw new IllegalArgumentException(
                "hard cooldown deferral requires "
                    + "INSIDE_HARD_COOLDOWN recency"
            );
        }

        if (priorityPosition == null) {

            throw new IllegalArgumentException(
                "eligible candidate must have priorityPosition"
            );
        }
    }
}
