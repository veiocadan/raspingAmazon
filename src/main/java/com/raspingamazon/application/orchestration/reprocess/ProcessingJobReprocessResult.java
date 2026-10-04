package com.raspingamazon.application.orchestration.reprocess;

import com.raspingamazon.application.orchestration.ProcessingJob;

import java.util.Objects;

/**
 * Resultado de uma solicitação idempotente de reprocessamento.
 *
 * @param reprocessEventId identidade da decisão operacional auditada
 * @param requestKey chave idempotente da decisão
 * @param job fotografia atual do ProcessingJob
 * @param newlyApplied true quando esta chamada aplicou a transição;
 *                     false quando a mesma requestKey já havia sido
 *                     aplicada anteriormente
 */
public record ProcessingJobReprocessResult(
    long reprocessEventId,
    String requestKey,
    ProcessingJob job,
    boolean newlyApplied
) {

    public ProcessingJobReprocessResult {

        if (reprocessEventId <= 0L) {

            throw new IllegalArgumentException(
                "reprocessEventId must be positive"
            );
        }

        requestKey =
            requireText(
                requestKey,
                "requestKey"
            );

        job =
            Objects.requireNonNull(
                job,
                "job must not be null"
            );
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return trimmed;
    }
}
