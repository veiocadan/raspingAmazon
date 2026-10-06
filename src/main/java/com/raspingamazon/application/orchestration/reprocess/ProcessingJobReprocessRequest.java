package com.raspingamazon.application.orchestration.reprocess;

import java.util.Objects;

/**
 * Solicitação explícita de reprocessamento de um ProcessingJob
 * terminal.
 *
 * <p>O reprocessamento é uma ação operacional controlada. Ele não é
 * produzido pelo retry automático do worker.</p>
 *
 * <p>{@code requestKey} representa a identidade idempotente da
 * decisão operacional. Repetir a mesma solicitação com a mesma chave
 * não deve aplicar o reprocessamento uma segunda vez.</p>
 */
public record ProcessingJobReprocessRequest(
    long processingJobId,
    String requestKey,
    String requestedBy,
    String reason
) {

    public ProcessingJobReprocessRequest {

        if (processingJobId <= 0L) {

            throw new IllegalArgumentException(
                "processingJobId must be positive"
            );
        }

        requestKey =
            requireText(
                requestKey,
                "requestKey"
            );

        requestedBy =
            requireText(
                requestedBy,
                "requestedBy"
            );

        reason =
            requireText(
                reason,
                "reason"
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
