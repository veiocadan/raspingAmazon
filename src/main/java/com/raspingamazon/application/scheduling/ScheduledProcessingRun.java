package com.raspingamazon.application.scheduling;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Resultado de uma janela de scheduling que efetivamente originou
 * uma ProcessingRun e seu primeiro ProcessingJob.
 */
public record ScheduledProcessingRun(

    String scheduleKey,

    /**
     * Janela lógica adquirida do scheduler.
     */
    OffsetDateTime scheduledFor,

    long processingRunId,

    long processingJobId,

    /**
     * Próxima janela persistida após a confirmação.
     */
    OffsetDateTime nextRunAt
) {

    public ScheduledProcessingRun {

        scheduleKey =
            requireText(
                scheduleKey,
                "ScheduledProcessingRun scheduleKey must not be blank"
            );

        Objects.requireNonNull(
            scheduledFor,
            "ScheduledProcessingRun scheduledFor must not be null"
        );

        if (processingRunId <= 0) {
            throw new IllegalArgumentException(
                "ScheduledProcessingRun processingRunId must be positive"
            );
        }

        if (processingJobId <= 0) {
            throw new IllegalArgumentException(
                "ScheduledProcessingRun processingJobId must be positive"
            );
        }

        Objects.requireNonNull(
            nextRunAt,
            "ScheduledProcessingRun nextRunAt must not be null"
        );

        if (!nextRunAt.isAfter(
            scheduledFor
        )) {

            throw new IllegalArgumentException(
                "ScheduledProcessingRun nextRunAt "
                    + "must be after scheduledFor"
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
}
