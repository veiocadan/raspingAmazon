package com.raspingamazon.application.orchestration;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Representa uma unidade durável de trabalho da orquestração.
 *
 * <p>Um ProcessingJob pode sobreviver ao encerramento da JVM,
 * ser reivindicado posteriormente por outro worker e ser
 * reexecutado conforme sua política de tentativas.</p>
 *
 * <p>Cada tipo de job possui exatamente um sujeito:</p>
 *
 * <pre>
 * COLLECT_DEALS
 *     -> processingRunId
 *
 * ENRICH_DEAL
 *     -> dealCandidateId
 *
 * EVALUATE_DEAL
 *     -> offerSnapshotId
 * </pre>
 */
public record ProcessingJob(

    /**
     * Identidade persistente do job.
     *
     * <p>Pode ser null antes da primeira persistência.</p>
     */
    Long id,

    ProcessingJobType type,

    ProcessingJobStatus status,

    Long processingRunId,

    Long dealCandidateId,

    Long offerSnapshotId,

    /**
     * Identidade lógica do trabalho.
     *
     * <p>Em conjunto com o tipo do job, impede a criação
     * duplicada da mesma unidade lógica.</p>
     */
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

    public ProcessingJob {

        if (id != null && id <= 0) {
            throw new IllegalArgumentException(
                "ProcessingJob id must be positive when present"
            );
        }

        Objects.requireNonNull(
            type,
            "ProcessingJob type must not be null"
        );

        Objects.requireNonNull(
            status,
            "ProcessingJob status must not be null"
        );

        idempotencyKey =
            requireNonBlank(
                idempotencyKey,
                "ProcessingJob idempotencyKey must not be blank"
            );

        if (attemptCount < 0) {
            throw new IllegalArgumentException(
                "ProcessingJob attemptCount must not be negative"
            );
        }

        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                "ProcessingJob maxAttempts must be positive"
            );
        }

        if (attemptCount > maxAttempts) {
            throw new IllegalArgumentException(
                "ProcessingJob attemptCount must not exceed maxAttempts"
            );
        }

        Objects.requireNonNull(
            availableAt,
            "ProcessingJob availableAt must not be null"
        );

        validateSubject(
            type,
            processingRunId,
            dealCandidateId,
            offerSnapshotId
        );

        validateLock(
            status,
            lockedAt,
            lockedBy
        );

        validateFinishedAt(
            status,
            finishedAt
        );
    }

    /**
     * Retorna true quando o job ainda possui tentativa disponível.
     */
    public boolean canRetry() {

        return attemptCount < maxAttempts;
    }

    /**
     * Retorna true para estados terminais.
     */
    public boolean finished() {

        return status == ProcessingJobStatus.SUCCEEDED
            || status == ProcessingJobStatus.DEAD;
    }

    private static void validateSubject(
        ProcessingJobType type,
        Long processingRunId,
        Long dealCandidateId,
        Long offerSnapshotId
    ) {

        switch (type) {

            case COLLECT_DEALS -> {

                requirePositive(
                    processingRunId,
                    "COLLECT_DEALS requires processingRunId"
                );

                requireNull(
                    dealCandidateId,
                    "COLLECT_DEALS must not have dealCandidateId"
                );

                requireNull(
                    offerSnapshotId,
                    "COLLECT_DEALS must not have offerSnapshotId"
                );
            }

            case ENRICH_DEAL -> {

                requireNull(
                    processingRunId,
                    "ENRICH_DEAL must not have processingRunId"
                );

                requirePositive(
                    dealCandidateId,
                    "ENRICH_DEAL requires dealCandidateId"
                );

                requireNull(
                    offerSnapshotId,
                    "ENRICH_DEAL must not have offerSnapshotId"
                );
            }

            case EVALUATE_DEAL -> {

                requireNull(
                    processingRunId,
                    "EVALUATE_DEAL must not have processingRunId"
                );

                requireNull(
                    dealCandidateId,
                    "EVALUATE_DEAL must not have dealCandidateId"
                );

                requirePositive(
                    offerSnapshotId,
                    "EVALUATE_DEAL requires offerSnapshotId"
                );
            }
        }
    }

    private static void validateLock(
        ProcessingJobStatus status,
        OffsetDateTime lockedAt,
        String lockedBy
    ) {

        if (status == ProcessingJobStatus.RUNNING) {

            Objects.requireNonNull(
                lockedAt,
                "RUNNING ProcessingJob requires lockedAt"
            );

            requireNonBlank(
                lockedBy,
                "RUNNING ProcessingJob requires lockedBy"
            );

            return;
        }

        if (lockedAt != null || lockedBy != null) {
            throw new IllegalArgumentException(
                "Only RUNNING ProcessingJob may hold a worker lock"
            );
        }
    }

    private static void validateFinishedAt(
        ProcessingJobStatus status,
        OffsetDateTime finishedAt
    ) {

        boolean terminal =
            status == ProcessingJobStatus.SUCCEEDED
                || status == ProcessingJobStatus.DEAD;

        if (terminal && finishedAt == null) {
            throw new IllegalArgumentException(
                "Terminal ProcessingJob requires finishedAt"
            );
        }

        if (!terminal && finishedAt != null) {
            throw new IllegalArgumentException(
                "Non-terminal ProcessingJob must not have finishedAt"
            );
        }
    }

    private static void requirePositive(
        Long value,
        String message
    ) {

        if (value == null || value <= 0) {
            throw new IllegalArgumentException(
                message
            );
        }
    }

    private static void requireNull(
        Long value,
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
