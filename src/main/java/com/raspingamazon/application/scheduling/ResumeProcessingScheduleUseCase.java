package com.raspingamazon.application.scheduling;

import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Retoma um ProcessingSchedule pausado.
 *
 * <p>A retomada torna a próxima janela imediatamente elegível,
 * utilizando o instante corrente como nextRunAt.</p>
 *
 * <p>A persistência deve impedir a operação enquanto existir um
 * lease ainda válido. Isso evita modificar a janela pertencente a
 * uma execução já em andamento.</p>
 */
public final class ResumeProcessingScheduleUseCase {

    private final ProcessingSchedulePort schedulePort;

    private final Clock clock;

    public ResumeProcessingScheduleUseCase(
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

        String validatedScheduleKey =
            requireExistingSchedule(
                scheduleKey
            );

        OffsetDateTime resumedAt =
            OffsetDateTime.now(
                clock
            );

        /*
         * Ao retomar, a execução volta a ser elegível imediatamente.
         *
         * Não tentamos reproduzir janelas perdidas durante a pausa.
         */
        OffsetDateTime nextRunAt =
            resumedAt;

        return schedulePort.resume(
            validatedScheduleKey,
            nextRunAt,
            resumedAt
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
