package com.raspingamazon.application.observability;

import com.raspingamazon.application.orchestration.ProcessingFailureType;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Fato operacional sobre uma chamada a uma integração.
 *
 * <p>A observação representa uma única operação concreta executada
 * contra uma fronteira de integração.</p>
 *
 * <p>Ela não representa contador agregado. Contagens, médias e taxas
 * devem ser derivadas das observações persistidas para evitar fontes
 * concorrentes da mesma verdade.</p>
 *
 * <p>Nenhum payload externo, HTML, header, token, cookie ou mensagem
 * livre de exceção pertence a este contrato.</p>
 */
public record IntegrationObservation(
    Long id,
    OffsetDateTime observedAt,
    String integration,
    String operation,
    IntegrationObservationOutcome outcome,
    long durationMs,
    OperationalLogContext context,
    OperationalFailureOrigin failureOrigin,
    ProcessingFailureType failureType,
    String errorCode,
    Integer httpStatusCode
) {

    public IntegrationObservation {

        if (id != null
            && id <= 0L) {

            throw new IllegalArgumentException(
                "IntegrationObservation id "
                    + "must be positive when present"
            );
        }

        Objects.requireNonNull(
            observedAt,
            "IntegrationObservation observedAt must not be null"
        );

        integration =
            requireNonBlank(
                integration,
                "integration"
            );

        operation =
            requireNonBlank(
                operation,
                "operation"
            );

        Objects.requireNonNull(
            outcome,
            "IntegrationObservation outcome must not be null"
        );

        if (durationMs < 0L) {

            throw new IllegalArgumentException(
                "IntegrationObservation durationMs "
                    + "must not be negative"
            );
        }

        Objects.requireNonNull(
            context,
            "IntegrationObservation context must not be null"
        );

        validateHttpStatusCode(
            httpStatusCode
        );

        validateFailureMetadata(
            outcome,
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

    private static void validateHttpStatusCode(
        Integer httpStatusCode
    ) {

        if (httpStatusCode == null) {
            return;
        }

        if (httpStatusCode < 100
            || httpStatusCode > 599) {

            throw new IllegalArgumentException(
                "IntegrationObservation httpStatusCode "
                    + "must be between 100 and 599 when present"
            );
        }
    }

    private static void validateFailureMetadata(
        IntegrationObservationOutcome outcome,
        OperationalFailureOrigin failureOrigin,
        ProcessingFailureType failureType,
        String errorCode
    ) {

        if (outcome
            == IntegrationObservationOutcome.SUCCESS) {

            if (failureOrigin != null
                || failureType != null
                || errorCode != null) {

                throw new IllegalArgumentException(
                    "Successful IntegrationObservation "
                        + "must not contain failure metadata"
                );
            }

            return;
        }

        if (failureOrigin == null
            || failureType == null
            || errorCode == null) {

            throw new IllegalArgumentException(
                "Failed IntegrationObservation requires "
                    + "failureOrigin, failureType and errorCode"
            );
        }
    }

    private static String requireNonBlank(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            "IntegrationObservation "
                + fieldName
                + " must not be null"
        );

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                "IntegrationObservation "
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
                "IntegrationObservation "
                    + fieldName
                    + " must not be blank when present"
            );
        }

        return value;
    }
}
