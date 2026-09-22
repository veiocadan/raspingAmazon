package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.orchestration.ProcessingFailureType;

import java.util.Objects;

/**
 * Resultado normalizado da classificação de uma falha de processamento.
 *
 * <p>Este objeto não altera o estado de jobs. Ele apenas descreve como
 * uma falha deve ser tratada pela camada de orquestração.</p>
 */
public record FailureClassification(
    ProcessingFailureType type,
    String code,
    String message
) {

    public FailureClassification {

        Objects.requireNonNull(
            type,
            "type must not be null"
        );

        code =
            requireText(
                code,
                "code must not be blank"
            );

        message =
            requireText(
                message,
                "message must not be blank"
            );
    }

    public boolean retryable() {

        return type
            == ProcessingFailureType.TRANSIENT;
    }

    private static String requireText(
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
