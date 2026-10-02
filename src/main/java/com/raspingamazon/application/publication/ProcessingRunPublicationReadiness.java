package com.raspingamazon.application.publication;

import com.raspingamazon.application.orchestration.ProcessingRunStatus;

import java.util.Objects;

/**
 * Fotografia auditável da prontidão de uma ProcessingRun
 * para seleção/publicação.
 *
 * <p>Os candidatos são classificados em três grupos mutuamente
 * exclusivos:</p>
 *
 * <pre>
 * completedCandidates
 * +
 * inProgressCandidates
 * +
 * blockedCandidates
 * =
 * totalCandidates
 * </pre>
 */
public record ProcessingRunPublicationReadiness(
    long processingRunId,
    ProcessingRunStatus processingRunStatus,
    ProcessingRunPublicationReadinessStatus status,
    long totalCandidates,
    long completedCandidates,
    long inProgressCandidates,
    long blockedCandidates
) {

    public ProcessingRunPublicationReadiness {

        if (processingRunId <= 0L) {

            throw new IllegalArgumentException(
                "processingRunId must be positive"
            );
        }

        Objects.requireNonNull(
            processingRunStatus,
            "processingRunStatus must not be null"
        );

        Objects.requireNonNull(
            status,
            "status must not be null"
        );

        requireNonNegative(
            totalCandidates,
            "totalCandidates"
        );

        requireNonNegative(
            completedCandidates,
            "completedCandidates"
        );

        requireNonNegative(
            inProgressCandidates,
            "inProgressCandidates"
        );

        requireNonNegative(
            blockedCandidates,
            "blockedCandidates"
        );

        long classifiedCandidates =
            completedCandidates
                + inProgressCandidates
                + blockedCandidates;

        if (classifiedCandidates
            != totalCandidates) {

            throw new IllegalArgumentException(
                "candidate classifications must sum to "
                    + "totalCandidates"
            );
        }
    }

    /**
     * Constrói a fotografia e deriva o estado lógico global.
     *
     * <p>A derivação global pertence à aplicação; o adapter JDBC
     * fornece apenas a fotografia persistente da run e dos
     * candidatos.</p>
     */
    public static ProcessingRunPublicationReadiness from(
        long processingRunId,
        ProcessingRunStatus processingRunStatus,
        long totalCandidates,
        long completedCandidates,
        long inProgressCandidates,
        long blockedCandidates
    ) {

        Objects.requireNonNull(
            processingRunStatus,
            "processingRunStatus must not be null"
        );

        ProcessingRunPublicationReadinessStatus readinessStatus =
            switch (processingRunStatus) {

                case PENDING, RUNNING ->
                    ProcessingRunPublicationReadinessStatus
                        .IN_PROGRESS;

                case FAILED ->
                    ProcessingRunPublicationReadinessStatus
                        .BLOCKED;

                case COMPLETED -> {

                    if (blockedCandidates > 0L) {

                        yield ProcessingRunPublicationReadinessStatus
                            .BLOCKED;
                    }

                    if (inProgressCandidates > 0L) {

                        yield ProcessingRunPublicationReadinessStatus
                            .IN_PROGRESS;
                    }

                    yield ProcessingRunPublicationReadinessStatus
                        .READY;
                }
            };

        return new ProcessingRunPublicationReadiness(
            processingRunId,
            processingRunStatus,
            readinessStatus,
            totalCandidates,
            completedCandidates,
            inProgressCandidates,
            blockedCandidates
        );
    }

    public boolean ready() {

        return status
            == ProcessingRunPublicationReadinessStatus.READY;
    }

    public boolean inProgress() {

        return status
            == ProcessingRunPublicationReadinessStatus.IN_PROGRESS;
    }

    public boolean blocked() {

        return status
            == ProcessingRunPublicationReadinessStatus.BLOCKED;
    }

    private static void requireNonNegative(
        long value,
        String fieldName
    ) {

        if (value < 0L) {

            throw new IllegalArgumentException(
                fieldName + " must not be negative"
            );
        }
    }
}
