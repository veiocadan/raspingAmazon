package com.raspingamazon.application.publication.port;

import com.raspingamazon.application.publication.ProcessingRunPublicationReadiness;

import java.util.Optional;

/**
 * Consulta a prontidão persistente de uma ProcessingRun para
 * seleção/publicação automática.
 *
 * <p>A porta não modifica estado e não cria jobs.</p>
 */
@FunctionalInterface
public interface ProcessingRunPublicationReadinessQueryPort {

    Optional<ProcessingRunPublicationReadiness>
    findByProcessingRunId(
        long processingRunId
    );
}
