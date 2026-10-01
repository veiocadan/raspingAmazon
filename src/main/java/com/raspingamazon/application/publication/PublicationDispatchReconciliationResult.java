package com.raspingamazon.application.publication;

/**
 * Resultado observável de uma rodada de reconciliação da etapa
 * PUBLICATION_DISPATCH.
 *
 * <p>Nenhum dos contadores representa publicação efetivamente
 * enviada. Eles descrevem somente a criação do trabalho durável.</p>
 */
public record PublicationDispatchReconciliationResult(
    int inspectedRunCount,
    int readyRunCount,
    int inProgressRunCount,
    int blockedRunCount,
    int enqueuedJobCount
) {

    public PublicationDispatchReconciliationResult {

        requireNonNegative(
            inspectedRunCount,
            "inspectedRunCount"
        );

        requireNonNegative(
            readyRunCount,
            "readyRunCount"
        );

        requireNonNegative(
            inProgressRunCount,
            "inProgressRunCount"
        );

        requireNonNegative(
            blockedRunCount,
            "blockedRunCount"
        );

        requireNonNegative(
            enqueuedJobCount,
            "enqueuedJobCount"
        );

        int classified =
            readyRunCount
                + inProgressRunCount
                + blockedRunCount;

        if (classified != inspectedRunCount) {

            throw new IllegalArgumentException(
                "readiness classifications must sum to "
                    + "inspectedRunCount"
            );
        }

        if (enqueuedJobCount
            > readyRunCount + blockedRunCount) {

            throw new IllegalArgumentException(
                "enqueuedJobCount must not exceed actionable runs"
            );
        }
    }

    private static void requireNonNegative(
        int value,
        String fieldName
    ) {

        if (value < 0) {

            throw new IllegalArgumentException(
                fieldName + " must not be negative"
            );
        }
    }
}
