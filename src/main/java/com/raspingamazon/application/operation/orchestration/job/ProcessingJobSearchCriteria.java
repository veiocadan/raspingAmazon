package com.raspingamazon.application.operation.orchestration.job;

import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobType;

import java.time.OffsetDateTime;

/**
 * Critérios da consulta operacional de ProcessingJob.
 */
public record ProcessingJobSearchCriteria(
    ProcessingJobType type,
    ProcessingJobStatus status,
    ProcessingFailureType lastFailureType,
    Long processingRunId,
    Long dealCandidateId,
    Long offerSnapshotId,
    OffsetDateTime createdFrom,
    OffsetDateTime createdUntil,
    ProcessingJobCursor after,
    int limit
) {

    public static final int DEFAULT_LIMIT = 50;

    public static final int MAX_LIMIT = 200;

    public ProcessingJobSearchCriteria {

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

        if (createdFrom != null
            && createdUntil != null
            && createdFrom.isAfter(
            createdUntil
        )) {

            throw new IllegalArgumentException(
                "ProcessingJobSearchCriteria createdFrom "
                    + "must not be after createdUntil"
            );
        }

        if (limit <= 0) {
            throw new IllegalArgumentException(
                "ProcessingJobSearchCriteria limit must be positive"
            );
        }

        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                "ProcessingJobSearchCriteria limit must not exceed "
                    + MAX_LIMIT
            );
        }
    }

    public static ProcessingJobSearchCriteria firstPage() {

        return new ProcessingJobSearchCriteria(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            DEFAULT_LIMIT
        );
    }

    private static void requirePositiveWhenPresent(
        Long value,
        String fieldName
    ) {

        if (value != null
            && value <= 0L) {

            throw new IllegalArgumentException(
                "ProcessingJobSearchCriteria "
                    + fieldName
                    + " must be positive"
            );
        }
    }
}
