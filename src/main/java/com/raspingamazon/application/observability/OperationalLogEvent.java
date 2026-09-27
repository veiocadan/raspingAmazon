package com.raspingamazon.application.observability;

import com.raspingamazon.application.orchestration.ProcessingFailureType;

import java.util.Objects;

/**
 * Evento operacional estruturado produzido pela aplicação.
 *
 * <p>O evento contém somente dados estruturados necessários para
 * observabilidade. Ele não carrega stack traces, payloads externos,
 * HTML, tokens, cookies, headers ou outros dados potencialmente
 * sensíveis.</p>
 *
 * <p>O timestamp não pertence a este objeto. Ele é acrescentado
 * pelo adapter de logging no momento em que o evento é emitido.</p>
 */
public record OperationalLogEvent(
    OperationalLogLevel level,
    String event,
    String component,
    String operation,
    OperationalLogContext context,
    String outcome,
    Long durationMs,
    OperationalFailureOrigin failureOrigin,
    ProcessingFailureType failureType,
    String errorCode
) {

    public OperationalLogEvent {

        Objects.requireNonNull(
            level,
            "OperationalLogEvent level must not be null"
        );

        event =
            requireNonBlank(
                event,
                "event"
            );

        component =
            requireNonBlank(
                component,
                "component"
            );

        operation =
            requireNonBlank(
                operation,
                "operation"
            );

        Objects.requireNonNull(
            context,
            "OperationalLogEvent context must not be null"
        );

        outcome =
            requireNonBlank(
                outcome,
                "outcome"
            );

        if (durationMs != null
            && durationMs < 0L) {

            throw new IllegalArgumentException(
                "OperationalLogEvent durationMs "
                    + "must not be negative when present"
            );
        }

        validateFailureMetadata(
            failureOrigin,
            failureType,
            errorCode
        );

        errorCode =
            requireNonBlankWhenPresent(
                errorCode,
                "errorCode"
            );
    }

    private static void validateFailureMetadata(
        OperationalFailureOrigin failureOrigin,
        ProcessingFailureType failureType,
        String errorCode
    ) {

        boolean hasOrigin =
            failureOrigin != null;

        boolean hasType =
            failureType != null;

        boolean hasCode =
            errorCode != null;

        if (hasOrigin != hasType
            || hasOrigin != hasCode) {

            throw new IllegalArgumentException(
                "OperationalLogEvent failureOrigin, "
                    + "failureType and errorCode "
                    + "must be provided together"
            );
        }
    }

    private static String requireNonBlank(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            "OperationalLogEvent "
                + fieldName
                + " must not be null"
        );

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                "OperationalLogEvent "
                    + fieldName
                    + " must not be blank"
            );
        }

        return value;
    }

    private static String requireNonBlankWhenPresent(
        String value,
        String fieldName
    ) {

        if (value == null) {
            return null;
        }

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                "OperationalLogEvent "
                    + fieldName
                    + " must not be blank when present"
            );
        }

        return value;
    }
}
