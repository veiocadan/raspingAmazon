package com.raspingamazon.application.observability;

/**
 * Resultado de uma interação observada com uma integração.
 */
public enum IntegrationObservationOutcome {

    /**
     * A operação da integração foi concluída com sucesso.
     */
    SUCCESS,

    /**
     * A operação da integração terminou em falha.
     */
    FAILURE
}
