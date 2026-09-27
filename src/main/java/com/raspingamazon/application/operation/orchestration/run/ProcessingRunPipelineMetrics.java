package com.raspingamazon.application.operation.orchestration.run;

/**
 * Métricas operacionais do pipeline associadas a uma ProcessingRun.
 *
 * <p>Os valores representam fatos persistidos observáveis. Este read
 * model não executa regras comerciais e não recalcula elegibilidade,
 * score, momentum ou publicação.</p>
 *
 * <p>Semântica:</p>
 *
 * <ul>
 *     <li>collectedCandidates: DealCandidates pertencentes à run;</li>
 *     <li>enrichedCandidates: candidatos já correlacionados a um
 *     OfferSnapshot;</li>
 *     <li>evaluations: avaliações persistidas alcançáveis a partir
 *     dos snapshots correlacionados;</li>
 *     <li>eligibleEvaluations: avaliações persistidas elegíveis;</li>
 *     <li>rejectedEvaluations: avaliações persistidas não elegíveis;</li>
 *     <li>publicationsGenerated: Publications persistidas geradas
 *     a partir das avaliações da run.</li>
 * </ul>
 *
 * <p>publicationsGenerated significa geração persistida. Não significa
 * envio, entrega ou publicação em canal externo.</p>
 */
public record ProcessingRunPipelineMetrics(
    long collectedCandidates,
    long enrichedCandidates,
    long evaluations,
    long eligibleEvaluations,
    long rejectedEvaluations,
    long publicationsGenerated
) {

    public ProcessingRunPipelineMetrics {

        requireNonNegative(
            collectedCandidates,
            "collectedCandidates"
        );

        requireNonNegative(
            enrichedCandidates,
            "enrichedCandidates"
        );

        requireNonNegative(
            evaluations,
            "evaluations"
        );

        requireNonNegative(
            eligibleEvaluations,
            "eligibleEvaluations"
        );

        requireNonNegative(
            rejectedEvaluations,
            "rejectedEvaluations"
        );

        requireNonNegative(
            publicationsGenerated,
            "publicationsGenerated"
        );

        if (enrichedCandidates
            > collectedCandidates) {

            throw new IllegalArgumentException(
                "ProcessingRunPipelineMetrics enrichedCandidates "
                    + "must not exceed collectedCandidates"
            );
        }

        if (eligibleEvaluations
            + rejectedEvaluations
            != evaluations) {

            throw new IllegalArgumentException(
                "ProcessingRunPipelineMetrics evaluation breakdown "
                    + "must equal evaluations"
            );
        }
    }

    /**
     * Quantidade de candidatos que ainda não possuem correlação
     * persistida com OfferSnapshot.
     */
    public long pendingEnrichmentCandidates() {

        return collectedCandidates
            - enrichedCandidates;
    }

    public boolean hasRejectedEvaluations() {

        return rejectedEvaluations > 0L;
    }

    private static void requireNonNegative(
        long value,
        String fieldName
    ) {

        if (value < 0L) {

            throw new IllegalArgumentException(
                "ProcessingRunPipelineMetrics "
                    + fieldName
                    + " must not be negative"
            );
        }
    }
}
