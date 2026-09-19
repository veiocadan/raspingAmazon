package com.raspingamazon.infrastructure.persistence;

/**
 * Exceção utilizada quando a infraestrutura não consegue
 * iniciar, confirmar ou desfazer uma transação JDBC.
 */
public final class TransactionOperationException
        extends RuntimeException {

    public TransactionOperationException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}