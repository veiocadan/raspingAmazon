package com.raspingamazon.application.scheduling;

/**
 * Indica que uma operação administrativa foi solicitada para um
 * ProcessingSchedule inexistente.
 *
 * <p>A exceção pertence à aplicação para que interfaces operacionais
 * possam distinguir recurso inexistente de falha técnica.</p>
 */
public final class ProcessingScheduleNotFoundException
    extends RuntimeException {

    public ProcessingScheduleNotFoundException(
        String scheduleKey
    ) {

        super(
            "ProcessingSchedule not found: "
                + scheduleKey
        );
    }
}
