package com.raspingamazon.infrastructure.publication.http;

/**
 * Falha ocorrida durante a execução material de uma
 * requisição HTTP de publicação.
 *
 * <p>A exceção representa problemas de transporte, como
 * timeout, interrupção, falha de conexão ou erro de I/O.
 * Respostas HTTP válidas, mesmo 4xx ou 5xx, não são
 * representadas por esta exceção.</p>
 */
public final class PublicationHttpTransportException
    extends RuntimeException {

    public PublicationHttpTransportException(
        String message,
        Throwable cause
    ) {

        super(
            message,
            cause
        );
    }
}
