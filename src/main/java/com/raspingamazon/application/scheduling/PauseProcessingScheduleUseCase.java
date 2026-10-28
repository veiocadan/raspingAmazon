package com.raspingamazon.application.scheduling;

import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Suspende a criação de novas execuções automáticas.
 *
 * <p>A pausa não cancela uma execução que já tenha adquirido lease.
 * ProcessingRuns, ProcessingJobs e histórico existentes permanecem
 * intactos.</p>
 */
public final class PauseProcessingScheduleUseCase {

    private final ProcessingSchedulePort schedulePort;

    private final Clock clock;

    public PauseProcessingScheduleUseCase(
        ProcessingSchedulePort schedulePort,
        Clock clock
    ) {

        this.schedulePort =
            Objects.requireNonNull(
                schedulePort,
                "schedulePort must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    public ProcessingSchedule execute(
        String scheduleKey
    ) {
        return execute(scheduleKey, ScheduleAdministrativeContext.useCaseReference("pause"));
    }

    public ProcessingSchedule execute(
        String scheduleKey,
        ScheduleAdministrativeContext context
    ) {
        Objects.requireNonNull(context, "context must not be null");

        String validatedScheduleKey =
            requireExistingSchedule(
                scheduleKey
            );

        OffsetDateTime changedAt =
            OffsetDateTime.now(
                clock
            );

        return schedulePort.pause(
            validatedScheduleKey,
            changedAt,
            context
        );
    }

    private String requireExistingSchedule(
        String scheduleKey
    ) {

        String validatedScheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        if (schedulePort.findByKey(
            validatedScheduleKey
        ).isEmpty()) {

            throw new ProcessingScheduleNotFoundException(
                validatedScheduleKey
            );
        }

        return validatedScheduleKey;
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
