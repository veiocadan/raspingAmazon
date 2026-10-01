package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.selection.PublicationSelectionExecution;

import java.util.Objects;

/**
 * Resultado observável do processamento de publicação originado
 * por uma ProcessingRun.
 *
 * <p>Preserva a correlação entre:</p>
 *
 * <pre>
 * ProcessingRun
 *      |
 *      v
 * candidatos persistidos
 *      |
 *      v
 * SelectionRun auditável
 *      |
 *      v
 * dispatch das Publications selecionadas
 * </pre>
 */
public record PublicationProcessingRunDispatchResult(
    long processingRunId,
    int sourceCandidateCount,
    PublicationSelectionExecution selectionExecution,
    PublicationSelectionDispatchResult dispatchResult
) {

    public PublicationProcessingRunDispatchResult {

        if (processingRunId <= 0L) {

            throw new IllegalArgumentException(
                "processingRunId must be positive"
            );
        }

        if (sourceCandidateCount < 0) {

            throw new IllegalArgumentException(
                "sourceCandidateCount must not be negative"
            );
        }

        Objects.requireNonNull(
            selectionExecution,
            "selectionExecution must not be null"
        );

        Objects.requireNonNull(
            dispatchResult,
            "dispatchResult must not be null"
        );

        if (dispatchResult.selectionRunId()
            != selectionExecution.auditRunId()) {

            throw new IllegalArgumentException(
                "dispatchResult selectionRunId must match "
                    + "selectionExecution auditRunId"
            );
        }
    }

    public long selectedCount() {

        return selectionExecution.result()
            .selectedCount();
    }

    public long reservedCount() {

        return dispatchResult.reservedCount();
    }
}
