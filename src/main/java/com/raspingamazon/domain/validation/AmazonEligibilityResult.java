package com.raspingamazon.domain.validation;

import com.raspingamazon.domain.evaluation.RejectionReason;

import java.util.Objects;

/**
 * Resultado da validação de elegibilidade de uma oferta Amazon.
 *
 * <p>Este objeto representa somente o resultado da validação.
 * Não contém regras de filtro, score ou momentum.</p>
 */
public record AmazonEligibilityResult(
        boolean eligible,
        RejectionReason rejectionReason
) {

    public AmazonEligibilityResult {
        if (eligible && rejectionReason != null) {
            throw new IllegalArgumentException(
                    "Eligible result must not have a rejection reason"
            );
        }

        if (!eligible && rejectionReason == null) {
            throw new IllegalArgumentException(
                    "Ineligible result must have a rejection reason"
            );
        }
    }

    public static AmazonEligibilityResult accepted() {
        return new AmazonEligibilityResult(
                true,
                null
        );
    }

    public static AmazonEligibilityResult rejected(
            RejectionReason rejectionReason
    ) {
        return new AmazonEligibilityResult(
                false,
                Objects.requireNonNull(
                        rejectionReason,
                        "rejectionReason must not be null"
                )
        );
    }
}