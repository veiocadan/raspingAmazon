package com.raspingamazon.application.scheduling;

import com.raspingamazon.application.scheduling.port.ProcessingSchedulePort;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Garante que um ProcessingSchedule inicial exista.
 *
 * <p>Este caso de uso é destinado ao bootstrap do runtime contínuo.
 * Ele não redefine configurações operacionais já persistidas.</p>
 *
 * <p>Na primeira execução:</p>
 *
 * <pre>
 * configuração inicial
 *        ↓
 * processing_schedule
 * </pre>
 *
 * <p>Nas execuções seguintes, saveIfAbsent devolve o estado já
 * persistido. Dessa forma, alterações realizadas pela interface
 * operacional — pause, resume ou interval — continuam sendo a fonte
 * de verdade.</p>
 */
public final class EnsureProcessingScheduleUseCase {

    private final ProcessingSchedulePort schedulePort;

    private final Clock clock;

    public EnsureProcessingScheduleUseCase(
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

    /**
     * Garante a existência do schedule.
     *
     * <p>A primeira janela é imediatamente elegível. Isso permite que
     * um runtime recém-configurado execute seu primeiro ciclo sem
     * aguardar um intervalo completo.</p>
     *
     * @param scheduleKey identidade lógica do schedule
     * @param source fonte da ProcessingRun
     * @param initialInterval frequência utilizada somente na criação
     * @return estado persistido, novo ou já existente
     */
    public ProcessingSchedule execute(
        String scheduleKey,
        URI source,
        Duration initialInterval
    ) {

        String validatedScheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        URI validatedSource =
            Objects.requireNonNull(
                source,
                "source must not be null"
            );

        if (!validatedSource.isAbsolute()) {

            throw new IllegalArgumentException(
                "source must be absolute"
            );
        }

        Duration validatedInterval =
            requirePositiveDuration(
                initialInterval
            );

        OffsetDateTime now =
            OffsetDateTime.now(
                clock
            );

        ProcessingSchedule initial =
            new ProcessingSchedule(
                validatedScheduleKey,
                validatedSource,
                true,
                validatedInterval,
                now,
                null,
                null,
                null,
                null,
                now,
                now
            );

        return schedulePort.saveIfAbsent(
            initial
        );
    }

    private Duration requirePositiveDuration(
        Duration duration
    ) {

        Objects.requireNonNull(
            duration,
            "initialInterval must not be null"
        );

        if (duration.isZero()
            || duration.isNegative()) {

            throw new IllegalArgumentException(
                "initialInterval must be positive"
            );
        }

        return duration;
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
