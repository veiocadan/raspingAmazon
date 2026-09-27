package com.raspingamazon.application.scheduling;

import java.util.Optional;

/**
 * Fronteira funcional utilizada pelo runtime para solicitar uma
 * execução agendada.
 *
 * <p>O runtime não precisa conhecer repositórios, PostgreSQL,
 * ProcessingJob ou detalhes transacionais. Ele apenas solicita que a
 * janela corrente seja processada quando elegível.</p>
 *
 * <p>ScheduleProcessingRunUseCase satisfaz este contrato através de
 * method reference:</p>
 *
 * <pre>
 * useCase::execute
 * </pre>
 */
@FunctionalInterface
public interface ScheduledProcessingTrigger {

    Optional<ScheduledProcessingRun> execute(
        String scheduleKey,
        String schedulerInstanceId
    );
}
