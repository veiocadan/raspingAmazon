package com.raspingamazon.application.observability;

/**
 * Origem operacional de uma falha observada.
 *
 * <p>Esta dimensão é independente de ProcessingFailureType.</p>
 *
 * <p>Por exemplo, uma falha pode ser simultaneamente:</p>
 *
 * <pre>
 * EXTERNAL + TRANSIENT
 * INTERNAL + PERMANENT
 * </pre>
 */
public enum OperationalFailureOrigin {

    /**
     * Falha ocorrida em uma integração ou sistema externo.
     */
    EXTERNAL,

    /**
     * Falha originada dentro da aplicação ou de sua infraestrutura
     * controlada.
     */
    INTERNAL
}
