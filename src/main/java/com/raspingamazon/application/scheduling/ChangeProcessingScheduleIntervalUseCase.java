package com.raspingamazon.application.scheduling;

import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Altera a frequência operacional de um ProcessingSchedule.
 *
 * <p>A nova cadência passa a ser calculada a partir do instante da
 * alteração:</p>
 *
 * <pre>
 * changedAt + interval
 *     =
 * nextRunAt
 * </pre>
 *
 * <p>Isso evita que uma configuração nova herde acidentalmente uma
 * janela vencida calculada segundo a frequência anterior.</p>
 *
 * <p>A persistência deve impedir a alteração enquanto existir um
 * lease ainda válido.</p>
 */
public final class ChangeProcessingScheduleIntervalUseCase {

    private final ProcessingSchedulePort schedulePort;

    private final Clock clock;

    public ChangeProcessingScheduleIntervalUseCase(
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
        String scheduleKey,
        Duration interval
    ) {

        String validatedScheduleKey =
            requireExistingSchedule(
                scheduleKey
            );

        Duration validatedInterval =
            requirePositiveDuration(
                interval
            );

        OffsetDateTime changedAt =
            OffsetDateTime.now(
                clock
            );

        OffsetDateTime nextRunAt =
            addDuration(
                changedAt,
                validatedInterval
            );

        return schedulePort.changeInterval(
            validatedScheduleKey,
            validatedInterval,
            nextRunAt,
            changedAt
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

    private Duration requirePositiveDuration(
        Duration interval
    ) {

        Objects.requireNonNull(
            interval,
            "interval must not be null"
        );

        if (interval.isZero()
            || interval.isNegative()) {

            throw new IllegalArgumentException(
                "interval must be positive"
            );
        }

        return interval;
    }

    private OffsetDateTime addDuration(
        OffsetDateTime instant,
        Duration duration
    ) {

        try {

            return instant.plus(
                duration
            );

        } catch (DateTimeException
                 | ArithmeticException exception) {

            throw new IllegalArgumentException(
                "interval cannot be represented from current time",
                exception
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
