package com.raspingamazon.infrastructure.bootstrap;

/**
 * Falha estrutural ao montar ou encerrar o runtime contínuo.
 */
public final class ContinuousProcessingBootstrapException
    extends RuntimeException {

    public ContinuousProcessingBootstrapException(
        String message,
        Throwable cause
    ) {

        super(
            message,
            cause
        );
    }
}
