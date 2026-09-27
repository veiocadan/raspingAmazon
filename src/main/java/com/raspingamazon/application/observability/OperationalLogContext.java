package com.raspingamazon.application.observability;

import com.raspingamazon.application.orchestration.ProcessingJobType;

/**
 * Dimensões de correlação associadas a um evento operacional.
 *
 * <p>Os campos são opcionais porque nem todo evento conhece toda a
 * linhagem naquele instante. Quando uma identidade for conhecida,
 * ela deve ser registrada explicitamente.</p>
 *
 * <p>Este objeto não representa a identidade de um ProcessingJob.
 * Portanto, múltiplas identidades da cadeia podem coexistir para
 * fornecer correlação observável.</p>
 */
public record OperationalLogContext(
    Long runId,
    Long jobId,
    ProcessingJobType jobType,
    Long candidateId,
    Long snapshotId,
    Long evaluationId,
    Long publicationId,
    String asin,
    String integration
) {

    public OperationalLogContext {

        requirePositiveWhenPresent(
            runId,
            "runId"
        );

        requirePositiveWhenPresent(
            jobId,
            "jobId"
        );

        requirePositiveWhenPresent(
            candidateId,
            "candidateId"
        );

        requirePositiveWhenPresent(
            snapshotId,
            "snapshotId"
        );

        requirePositiveWhenPresent(
            evaluationId,
            "evaluationId"
        );

        requirePositiveWhenPresent(
            publicationId,
            "publicationId"
        );

        asin =
            requireNonBlankWhenPresent(
                asin,
                "asin"
            );

        integration =
            requireNonBlankWhenPresent(
                integration,
                "integration"
            );
    }

    /**
     * Contexto sem correlação disponível.
     *
     * <p>Útil para eventos operacionais globais que ainda não estejam
     * associados a uma execução ou entidade persistida.</p>
     */
    public static OperationalLogContext empty() {

        return new OperationalLogContext(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private static void requirePositiveWhenPresent(
        Long value,
        String fieldName
    ) {

        if (value != null
            && value <= 0L) {

            throw new IllegalArgumentException(
                "OperationalLogContext "
                    + fieldName
                    + " must be positive when present"
            );
        }
    }

    private static String requireNonBlankWhenPresent(
        String value,
        String fieldName
    ) {

        if (value == null) {
            return null;
        }

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                "OperationalLogContext "
                    + fieldName
                    + " must not be blank when present"
            );
        }

        return value;
    }
}
