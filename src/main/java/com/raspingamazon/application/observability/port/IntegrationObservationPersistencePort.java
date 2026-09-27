package com.raspingamazon.application.observability.port;

import com.raspingamazon.application.observability.IntegrationObservation;

/**
 * Porta de persistência das observações duráveis de integração.
 *
 * <p>O contrato pertence à aplicação. JDBC, PostgreSQL ou qualquer
 * outro mecanismo concreto permanecem na infraestrutura.</p>
 */
public interface IntegrationObservationPersistencePort {

    /**
     * Persiste uma observação de integração.
     *
     * @param observation observação ainda não persistida ou
     *                    representação válida a ser armazenada
     *
     * @return observação persistida com identidade
     */
    IntegrationObservation save(
        IntegrationObservation observation
    );
}
