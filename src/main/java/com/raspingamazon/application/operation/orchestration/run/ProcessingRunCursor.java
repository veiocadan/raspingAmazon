package com.raspingamazon.application.operation.orchestration.run;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Cursor da paginação operacional de ProcessingRun.
 *
 * <p>A ordenação contratada é:</p>
 *
 * <pre>
 * requestedAt DESC
 * runId DESC
 * </pre>
 */
public record ProcessingRunCursor(
    OffsetDateTime requestedAt,
    long runId
) {

    public ProcessingRunCursor {

        Objects.requireNonNull(
            requestedAt,
            "ProcessingRunCursor requestedAt must not be null"
        );

        if (runId <= 0L) {
            throw new IllegalArgumentException(
                "ProcessingRunCursor runId must be positive"
            );
        }
    }
}
