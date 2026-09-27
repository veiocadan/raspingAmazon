package com.raspingamazon.infrastructure.runtime;

/**
 * Falha terminal do runtime de processamento contínuo.
 *
 * <p>Esta exceção representa a interrupção anormal de um dos loops
 * operacionais coordenados pelo ContinuousProcessingRuntime.</p>
 *
 * <p>Ela não classifica falhas funcionais de coleta, enrichment,
 * avaliação ou publicação. Essas falhas continuam pertencendo às
 * políticas específicas da orquestração.</p>
 */
public final class ContinuousProcessingRuntimeException
    extends RuntimeException {

    public ContinuousProcessingRuntimeException(
        String message,
        Throwable cause
    ) {

        super(
            message,
            cause
        );
    }
}
