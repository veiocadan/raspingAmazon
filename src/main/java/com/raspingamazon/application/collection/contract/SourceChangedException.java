package com.raspingamazon.application.collection.contract;

import java.util.Objects;

/**
 * Indica que a fonte externa respondeu com conteúdo utilizável em nível
 * de transporte, porém a estrutura observada não corresponde mais ao
 * contrato estrutural conhecido pela aplicação.
 *
 * <p>Esta exceção pertence ao contrato da aplicação porque SOURCE_CHANGED
 * é uma condição operacional relevante independentemente do adapter
 * concreto utilizado para adquirir o conteúdo.</p>
 *
 * <p>Ela não transporta o documento completo, cookies, tokens,
 * screenshots ou qualquer material destinado a contornar proteções da
 * fonte.</p>
 */
public final class SourceChangedException
    extends CollectionException {

    private final String errorCode;

    public SourceChangedException(
        String errorCode,
        String message
    ) {

        super(
            requireText(
                message,
                "message must not be blank"
            )
        );

        this.errorCode =
            requireText(
                errorCode,
                "errorCode must not be blank"
            );
    }

    public String errorCode() {

        return errorCode;
    }

    private static String requireText(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {

            throw new IllegalArgumentException(
                message
            );
        }

        return normalized;
    }
}
