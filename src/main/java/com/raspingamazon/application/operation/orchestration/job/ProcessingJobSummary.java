package com.raspingamazon.application.operation.orchestration.job;

import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobType;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Read model operacional de um ProcessingJob.
 *
 * <p>Este objeto representa fatos persistidos da fila e não
 * participa do processamento automático.</p>
 */
public record ProcessingJobSummary(
    long jobId,
    ProcessingJobType type,
    ProcessingJobStatus status,
    Long processingRunId,
    Long dealCandidateId,
    Long offerSnapshotId,
    String idempotencyKey,
    int attemptCount,
    int maxAttempts,
    OffsetDateTime availableAt,
    OffsetDateTime lockedAt,
    String lockedBy,
    ProcessingFailureType lastFailureType,
    String lastErrorCode,
    String lastErrorMessage,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime finishedAt
) {

    public ProcessingJobSummary {

        if (jobId <= 0L) {
            throw new IllegalArgumentException(
                "ProcessingJobSummary jobId must be positive"
            );
        }

        Objects.requireNonNull(
            type,
            "ProcessingJobSummary type must not be null"
        );

        Objects.requireNonNull(
            status,
            "ProcessingJobSummary status must not be null"
        );

        requirePositiveWhenPresent(
            processingRunId,
            "processingRunId"
        );

        requirePositiveWhenPresent(
            dealCandidateId,
            "dealCandidateId"
        );

        requirePositiveWhenPresent(
            offerSnapshotId,
            "offerSnapshotId"
        );

        idempotencyKey =
            requireText(
                idempotencyKey,
                "ProcessingJobSummary idempotencyKey must not be blank"
            );

        if (attemptCount < 0) {
            throw new IllegalArgumentException(
                "ProcessingJobSummary attemptCount must not be negative"
            );
        }

        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                "ProcessingJobSummary maxAttempts must be positive"
            );
        }

        if (attemptCount > maxAttempts) {
            throw new IllegalArgumentException(
                "ProcessingJobSummary attemptCount "
                    + "must not exceed maxAttempts"
            );
        }

        Objects.requireNonNull(
            availableAt,
            "ProcessingJobSummary availableAt must not be null"
        );

        lockedBy =
            optionalText(
                lockedBy,
                "ProcessingJobSummary lockedBy must not be blank"
            );

        lastErrorCode =
            optionalText(
                lastErrorCode,
                "ProcessingJobSummary lastErrorCode must not be blank"
            );

        lastErrorMessage =
            optionalText(
                lastErrorMessage,
                "ProcessingJobSummary lastErrorMessage must not be blank"
            );

        Objects.requireNonNull(
            createdAt,
            "ProcessingJobSummary createdAt must not be null"
        );

        Objects.requireNonNull(
            updatedAt,
            "ProcessingJobSummary updatedAt must not be null"
        );
    }

    public int remainingAttempts() {

        return maxAttempts
            - attemptCount;
    }

    public boolean terminal() {

        return status == ProcessingJobStatus.SUCCEEDED
            || status == ProcessingJobStatus.DEAD;
    }

    private static void requirePositiveWhenPresent(
        Long value,
        String fieldName
    ) {

        if (value != null
            && value <= 0L) {

            throw new IllegalArgumentException(
                "ProcessingJobSummary "
                    + fieldName
                    + " must be positive"
            );
        }
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
