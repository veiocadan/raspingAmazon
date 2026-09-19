package com.raspingamazon.infrastructure.persistence;

/**
 * Exceção não verificada utilizada pelos adapters de persistência
 * para impedir que detalhes JDBC escapem para a camada de aplicação.
 *
 * <p>A causa original é preservada para diagnóstico e observabilidade.</p>
 */
public final class PersistenceOperationException
        extends RuntimeException {

    public PersistenceOperationException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}