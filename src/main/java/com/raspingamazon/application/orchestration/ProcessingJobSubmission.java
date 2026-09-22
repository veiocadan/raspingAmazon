package com.raspingamazon.application.orchestration;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Solicitação para criação idempotente de um ProcessingJob.
 *
 * <p>Este contrato contém somente os dados que um caso de uso
 * precisa fornecer para solicitar trabalho à fila.</p>
 *
 * <p>Campos de execução como status, attemptCount, lockedAt e
 * lockedBy são responsabilidade da implementação da fila.</p>
 */
public record ProcessingJobSubmission(

    ProcessingJobType type,

    Long processingRunId,

    Long dealCandidateId,

    Long offerSnapshotId,

    String idempotencyKey,

    int maxAttempts,

    OffsetDateTime availableAt
) {

    public ProcessingJobSubmission {

        Objects.requireNonNull(
            type,
            "ProcessingJobSubmission type must not be null"
        );

        idempotencyKey = requireNonBlank(
            idempotencyKey,
            "ProcessingJobSubmission idempotencyKey must not be blank"
        );

        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                "ProcessingJobSubmission maxAttempts must be positive"
            );
        }

        Objects.requireNonNull(
            availableAt,
            "ProcessingJobSubmission availableAt must not be null"
        );

        validateSubject(
            type,
            processingRunId,
            dealCandidateId,
            offerSnapshotId
        );
    }

    public static ProcessingJobSubmission collectDeals(
        long processingRunId,
        String idempotencyKey,
        int maxAttempts,
        OffsetDateTime availableAt
    ) {

        return new ProcessingJobSubmission(
            ProcessingJobType.COLLECT_DEALS,
            processingRunId,
            null,
            null,
            idempotencyKey,
            maxAttempts,
            availableAt
        );
    }

    public static ProcessingJobSubmission enrichDeal(
        long dealCandidateId,
        String idempotencyKey,
        int maxAttempts,
        OffsetDateTime availableAt
    ) {

        return new ProcessingJobSubmission(
            ProcessingJobType.ENRICH_DEAL,
            null,
            dealCandidateId,
            null,
            idempotencyKey,
            maxAttempts,
            availableAt
        );
    }

    public static ProcessingJobSubmission evaluateDeal(
        long offerSnapshotId,
        String idempotencyKey,
        int maxAttempts,
        OffsetDateTime availableAt
    ) {

        return new ProcessingJobSubmission(
            ProcessingJobType.EVALUATE_DEAL,
            null,
            null,
            offerSnapshotId,
            idempotencyKey,
            maxAttempts,
            availableAt
        );
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
