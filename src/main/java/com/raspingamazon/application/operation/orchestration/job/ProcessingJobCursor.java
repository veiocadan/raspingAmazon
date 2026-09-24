package com.raspingamazon.application.operation.orchestration.job;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Cursor da paginação operacional de ProcessingJob.
 *
 * <p>A ordenação contratada é:</p>
 *
 * <pre>
 * createdAt DESC
 * jobId DESC
 * </pre>
 */
public record ProcessingJobCursor(
    OffsetDateTime createdAt,
    long jobId
) {

    public ProcessingJobCursor {

        Objects.requireNonNull(
            createdAt,
            "ProcessingJobCursor createdAt must not be null"
        );

        if (jobId <= 0L) {
            throw new IllegalArgumentException(
                "ProcessingJobCursor jobId must be positive"
            );
        }
    }
}
