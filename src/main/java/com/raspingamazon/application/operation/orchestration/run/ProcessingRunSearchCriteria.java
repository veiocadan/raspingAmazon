package com.raspingamazon.application.operation.orchestration.run;

import com.raspingamazon.application.orchestration.ProcessingRunStatus;

import java.time.OffsetDateTime;

/**
 * Critérios da listagem operacional de ProcessingRun.
 */
public record ProcessingRunSearchCriteria(
    ProcessingRunStatus status,
    OffsetDateTime requestedFrom,
    OffsetDateTime requestedUntil,
    ProcessingRunCursor after,
    int limit
) {

    public static final int DEFAULT_LIMIT = 50;

    public static final int MAX_LIMIT = 200;

    public ProcessingRunSearchCriteria {

        if (limit <= 0) {
            throw new IllegalArgumentException(
                "ProcessingRunSearchCriteria limit must be positive"
            );
        }

        if (limit > MAX_LIMIT) {
            throw new IllegalArgumentException(
                "ProcessingRunSearchCriteria limit must not exceed "
                    + MAX_LIMIT
            );
        }

        if (requestedFrom != null
            && requestedUntil != null
            && requestedFrom.isAfter(
            requestedUntil
        )) {

            throw new IllegalArgumentException(
                "ProcessingRunSearchCriteria requestedFrom "
                    + "must not be after requestedUntil"
            );
        }
    }

    public static ProcessingRunSearchCriteria firstPage() {

        return new ProcessingRunSearchCriteria(
            null,
            null,
            null,
            null,
            DEFAULT_LIMIT
        );
    }
}
