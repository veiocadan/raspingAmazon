package com.raspingamazon.infrastructure.composition;

/**
 * Falha de infraestrutura durante abertura ou encerramento
 * da composição da interface operacional.
 *
 * <p>A camada de apresentação não precisa conhecer SQLException
 * nem outros detalhes JDBC para utilizar a interface operacional.</p>
 */
public final class OperationalInterfaceCompositionException
    extends RuntimeException {

    public OperationalInterfaceCompositionException(
        String message,
        Throwable cause
    ) {

        super(
            message,
            cause
        );
    }
}
