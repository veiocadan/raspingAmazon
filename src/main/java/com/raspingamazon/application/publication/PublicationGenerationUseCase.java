package com.raspingamazon.application.publication;

import com.raspingamazon.domain.publication.Publication;

/**
 * Contrato do caso de uso de geração idempotente de Publication.
 *
 * <p>Recebe uma DealEvaluation persistida e devolve a Publication
 * correspondente à identidade versionada da geração.</p>
 */
@FunctionalInterface
public interface PublicationGenerationUseCase {

    Publication generate(
        long dealEvaluationId
    );
}
