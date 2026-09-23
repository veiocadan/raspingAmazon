package com.raspingamazon.application.orchestration;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Representa uma execução lógica de coleta.
 *
 * <p>Uma ProcessingRun identifica a coleta que originou um conjunto
 * de DealCandidates.</p>
 *
 * <p>Os jobs derivados possuem ciclo de vida independente da run.</p>
 */
public record ProcessingRun(

    /**
     * Identidade persistente.
     *
     * <p>Pode ser null antes da persistência.</p>
     */
    Long id,

    /**
     * Chave idempotente da execução.
     */
    String runKey,

    /**
     * Fonte solicitada para coleta.
     */
    URI source,

    ProcessingRunStatus status,

    OffsetDateTime requestedAt,

    OffsetDateTime startedAt,

    OffsetDateTime completedAt,

    String lastErrorCode,

    String lastErrorMessage
) {

    public ProcessingRun {

        if (id != null && id <= 0) {
            throw new IllegalArgumentException(
                "ProcessingRun id must be positive when present"
            );
        }

        runKey = requireNonBlank(
            runKey,
            "ProcessingRun runKey must not be blank"
        );

        Objects.requireNonNull(
            source,
            "ProcessingRun source must not be null"
        );

        if (!source.isAbsolute()) {
            throw new IllegalArgumentException(
                "ProcessingRun source must be absolute"
            );
        }

        Objects.requireNonNull(
            status,
            "ProcessingRun status must not be null"
        );

        Objects.requireNonNull(
            requestedAt,
            "ProcessingRun requestedAt must not be null"
        );

        validateLifecycle(
            status,
            requestedAt,
            startedAt,
            completedAt
        );
    }

    /**
     * Informa se a run já possui identidade persistente.
     */
    public boolean persisted() {

        return id != null;
    }

    private static void validateLifecycle(
        ProcessingRunStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
    ) {

        switch (status) {

            case PENDING -> {

                requireNull(
                    startedAt,
                    "PENDING ProcessingRun must not have startedAt"
                );

                requireNull(
                    completedAt,
                    "PENDING ProcessingRun must not have completedAt"
                );
            }

            case RUNNING -> {

                Objects.requireNonNull(
                    startedAt,
                    "RUNNING ProcessingRun requires startedAt"
                );

                requireNull(
                    completedAt,
                    "RUNNING ProcessingRun must not have completedAt"
                );
            }

            case COMPLETED, FAILED -> {

                Objects.requireNonNull(
                    startedAt,
                    "Finished ProcessingRun requires startedAt"
                );

                Objects.requireNonNull(
                    completedAt,
                    "Finished ProcessingRun requires completedAt"
                );
            }
        }

        if (startedAt != null
            && startedAt.isBefore(requestedAt)) {

            throw new IllegalArgumentException(
                "ProcessingRun startedAt must not be before requestedAt"
            );
        }

        if (completedAt != null
            && startedAt != null
            && completedAt.isBefore(startedAt)) {

            throw new IllegalArgumentException(
                "ProcessingRun completedAt must not be before startedAt"
            );
        }
    }

    private static void requireNull(
        Object value,
        String message
    ) {

        if (value != null) {
            throw new IllegalArgumentException(
                message
            );
        }
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
