package com.raspingamazon.application.collection.contract;

import java.util.Objects;

/**
 * Indica que a fonte externa foi alcançada, porém o recurso solicitado
 * está explicitamente indisponível ou inexistente.
 *
 * <p>Esta condição é diferente de:</p>
 *
 * <ul>
 *     <li>falha de transporte;</li>
 *     <li>rate limit;</li>
 *     <li>restrição da fonte;</li>
 *     <li>mudança estrutural da fonte;</li>
 *     <li>ausência de uma evidência opcional ou classificável como
 *         UNKNOWN dentro de um recurso válido.</li>
 * </ul>
 *
 * <p>A exceção pertence ao contrato da aplicação para que a política
 * operacional possa classificar DATA_UNAVAILABLE sem depender do adapter
 * Amazon concreto.</p>
 */
public final class SourceDataUnavailableException
    extends CollectionException {

    private final String errorCode;

    public SourceDataUnavailableException(
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
