package com.raspingamazon.application.orchestration;

import java.util.Objects;

/**
 * Descreve uma falha operacional de um ProcessingJob.
 *
 * <p>A classificação da falha é responsabilidade da aplicação.
 * A fila apenas persiste a decisão recebida.</p>
 */
public record ProcessingFailure(

    ProcessingFailureType type,

    String errorCode,

    String errorMessage
) {

    public ProcessingFailure {

        Objects.requireNonNull(
            type,
            "ProcessingFailure type must not be null"
        );

        errorCode = requireNonBlank(
            errorCode,
            "ProcessingFailure errorCode must not be blank"
        );
    }

    private static String requireNonBlank(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
