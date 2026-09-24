package com.raspingamazon.application.operation.orchestration.run;

import com.raspingamazon.application.orchestration.ProcessingRunStatus;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Read model da listagem operacional de ProcessingRun.
 *
 * <p>Não é utilizado pelo worker nem pelo pipeline automático.</p>
 */
public record ProcessingRunSummary(
    long runId,
    String runKey,
    String sourceUri,
    ProcessingRunStatus status,
    OffsetDateTime requestedAt,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    String lastErrorCode,
    String lastErrorMessage,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public ProcessingRunSummary {

        if (runId <= 0L) {
            throw new IllegalArgumentException(
                "ProcessingRunSummary runId must be positive"
            );
        }

        runKey =
            requireText(
                runKey,
                "ProcessingRunSummary runKey must not be blank"
            );

        sourceUri =
            requireText(
                sourceUri,
                "ProcessingRunSummary sourceUri must not be blank"
            );

        Objects.requireNonNull(
            status,
            "ProcessingRunSummary status must not be null"
        );

        Objects.requireNonNull(
            requestedAt,
            "ProcessingRunSummary requestedAt must not be null"
        );

        Objects.requireNonNull(
            createdAt,
            "ProcessingRunSummary createdAt must not be null"
        );

        Objects.requireNonNull(
            updatedAt,
            "ProcessingRunSummary updatedAt must not be null"
        );

        lastErrorCode =
            optionalText(
                lastErrorCode,
                "ProcessingRunSummary lastErrorCode must not be blank"
            );

        lastErrorMessage =
            optionalText(
                lastErrorMessage,
                "ProcessingRunSummary lastErrorMessage must not be blank"
            );
    }

    public boolean failed() {
        return status == ProcessingRunStatus.FAILED;
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

    private static String optionalText(
        String value,
        String message
    ) {

        if (value == null) {
            return null;
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
