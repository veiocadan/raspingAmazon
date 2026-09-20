package com.raspingamazon.application.collection.contract;

/**
 * Representa uma falha técnica ocorrida durante uma tentativa de coleta.
 *
 * <p>A exceção pertence ao contrato da aplicação porque uma implementação
 * concreta de Collector pode utilizar diferentes tecnologias de transporte
 * sem obrigar o restante da aplicação a conhecer essas tecnologias.</p>
 *
 * <p>Quando a falha decorre de uma resposta HTTP não aceita, o status HTTP
 * e um trecho diagnóstico do corpo podem ser preservados explicitamente.</p>
 */
public class CollectionException extends RuntimeException {

    private final Integer httpStatusCode;

    private final String responseBodyExcerpt;

    public CollectionException(
        String message
    ) {
        super(message);

        this.httpStatusCode = null;
        this.responseBodyExcerpt = null;
    }

    public CollectionException(
        String message,
        Throwable cause
    ) {
        super(
            message,
            cause
        );

        this.httpStatusCode = null;
        this.responseBodyExcerpt = null;
    }

    public CollectionException(
        String message,
        int httpStatusCode,
        String responseBodyExcerpt
    ) {
        super(message);

        if (httpStatusCode < 100
            || httpStatusCode > 599) {

            throw new IllegalArgumentException(
                "HTTP status code must be between 100 and 599"
            );
        }

        this.httpStatusCode =
            httpStatusCode;

        this.responseBodyExcerpt =
            responseBodyExcerpt;
    }

    public Integer httpStatusCode() {
        return httpStatusCode;
    }

    public String responseBodyExcerpt() {
        return responseBodyExcerpt;
    }
}
