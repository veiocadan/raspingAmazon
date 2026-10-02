package com.raspingamazon.application.publication;

/**
 * Caso de uso mínimo necessário para executar a cadeia automática
 * de publicação associada a uma ProcessingRun.
 *
 * <p>O chamador informa somente a identidade persistida da run.</p>
 *
 * <p>Channel, destination, quota, cooldown, cadência, geração,
 * readiness e outbox pertencem à composição interna da aplicação.</p>
 */
@FunctionalInterface
public interface PublicationProcessingRunDispatchUseCase {

    /**
     * Executa seleção e dispatch automático para a ProcessingRun.
     *
     * @param processingRunId identidade persistida da ProcessingRun
     * @return resultado observável do dispatch
     */
    PublicationSelectionDispatchResult process(
        long processingRunId
    );
}
