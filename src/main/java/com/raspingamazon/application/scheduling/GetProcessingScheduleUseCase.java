package com.raspingamazon.application.scheduling;

import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;

import java.util.Objects;
import java.util.Optional;

/**
 * Consulta o estado operacional persistido de um agendamento.
 *
 * <p>Não calcula regras, não altera lease e não inicia execução.</p>
 */
public final class GetProcessingScheduleUseCase {

    private final ProcessingSchedulePort schedulePort;

    public GetProcessingScheduleUseCase(
        ProcessingSchedulePort schedulePort
    ) {

        this.schedulePort =
            Objects.requireNonNull(
                schedulePort,
                "schedulePort must not be null"
            );
    }

    public Optional<ProcessingSchedule> execute(
        String scheduleKey
    ) {

        String validatedScheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        return schedulePort.findByKey(
            validatedScheduleKey
        );
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
