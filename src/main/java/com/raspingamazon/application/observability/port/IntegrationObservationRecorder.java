package com.raspingamazon.application.observability.port;

import com.raspingamazon.application.observability.IntegrationObservation;

/**
 * Fronteira utilizada pelas integrações para registrar observações
 * operacionais sem conhecer o mecanismo concreto de persistência.
 *
 * <p>A implementação padrão da FASE 16 utiliza comportamento
 * best-effort: falhas da própria observabilidade não podem alterar
 * o resultado funcional da integração observada.</p>
 */
@FunctionalInterface
public interface IntegrationObservationRecorder {

    /**
     * Registra uma observação operacional.
     *
     * @param observation observação válida a ser registrada
     */
    void record(
        IntegrationObservation observation
    );
}
