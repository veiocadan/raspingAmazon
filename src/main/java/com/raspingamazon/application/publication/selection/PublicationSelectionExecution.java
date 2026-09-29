package com.raspingamazon.application.publication.selection;

import com.raspingamazon.domain.publication.selection.PublicationSelectionResult;

import java.util.Objects;

/**
 * Resultado de aplicação de uma execução persistida da seleção.
 *
 * <p>Combina o resultado produzido pelo domínio com a identidade
 * da trilha auditável persistida.</p>
 */
public record PublicationSelectionExecution(
    long auditRunId,
    PublicationSelectionResult result
) {

    public PublicationSelectionExecution {

        if (auditRunId <= 0L) {

            throw new IllegalArgumentException(
                "auditRunId must be positive"
            );
        }

        Objects.requireNonNull(
            result,
            "result must not be null"
        );
    }
}
