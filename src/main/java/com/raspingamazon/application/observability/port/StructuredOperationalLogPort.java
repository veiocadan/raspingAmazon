package com.raspingamazon.application.observability.port;

import com.raspingamazon.application.observability.OperationalLogEvent;

/**
 * Porta da aplicação para emissão de eventos operacionais
 * estruturados.
 *
 * <p>A aplicação conhece somente este contrato. O destino concreto
 * pode ser JSON em stdout/stderr, um agente de coleta ou outra
 * implementação futura.</p>
 *
 * <p>Esta porta não representa armazenamento durável da verdade
 * operacional. PostgreSQL continua sendo a fonte persistente dos
 * estados e correlações do pipeline.</p>
 */
public interface StructuredOperationalLogPort {

    /**
     * Emite um evento operacional estruturado.
     *
     * @param event evento a ser emitido
     */
    void log(
        OperationalLogEvent event
    );
}
