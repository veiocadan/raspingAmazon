package com.raspingamazon.infrastructure.runtime;

import com.raspingamazon.application.scheduling.ScheduledProcessingTrigger;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Runtime operacional que verifica continuamente se uma janela de
 * processamento está pronta para ser disparada.
 *
 * <p>Esta classe não conhece:</p>
 *
 * <ul>
 *     <li>PostgreSQL;</li>
 *     <li>ProcessingRunRepository;</li>
 *     <li>ProcessingJobQueue;</li>
 *     <li>coleta;</li>
 *     <li>Amazon;</li>
 *     <li>parser;</li>
 *     <li>avaliação;</li>
 * </ul>
 *
 * <p>A atomicidade e a prevenção de sobreposição continuam
 * pertencendo ao ScheduleProcessingRunUseCase e ao
 * ProcessingSchedulePort.</p>
 *
 * <p>O runner sempre aguarda entre verificações, inclusive quando uma
 * janela acabou de ser disparada. Isso evita busy-loop contra a fonte
 * de verdade persistida.</p>
 *
 * <p>RuntimeExceptions não são transformadas em retry silencioso.
 * Políticas amplas de recuperação pertencem à FASE 20.</p>
 */
public final class ContinuousProcessingSchedulerRunner {

    private final ScheduledProcessingTrigger trigger;

    private final String scheduleKey;

    private final String schedulerInstanceId;

    private final Duration pollInterval;

    private final ProcessingSchedulerWaitStrategy waitStrategy;

    public ContinuousProcessingSchedulerRunner(
        ScheduledProcessingTrigger trigger,
        String scheduleKey,
        String schedulerInstanceId,
        Duration pollInterval,
        ProcessingSchedulerWaitStrategy waitStrategy
    ) {

        this.trigger =
            Objects.requireNonNull(
                trigger,
                "trigger must not be null"
            );

        this.scheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey must not be blank"
            );

        this.schedulerInstanceId =
            requireText(
                schedulerInstanceId,
                "schedulerInstanceId must not be blank"
            );

        this.pollInterval =
            requirePositiveDuration(
                pollInterval
            );

        this.waitStrategy =
            Objects.requireNonNull(
                waitStrategy,
                "waitStrategy must not be null"
            );
    }

    /**
     * Executa polling contínuo enquanto o sinal permanecer ativo.
     *
     * <p>O BooleanSupplier será compartilhado posteriormente com o
     * lifecycle do processo. Assim esta classe não precisa conhecer
     * shutdown hooks ou sinais da JVM.</p>
     */
    public void run(
        BooleanSupplier keepRunning
    ) {

        Objects.requireNonNull(
            keepRunning,
            "keepRunning must not be null"
        );

        while (keepRunning.getAsBoolean()) {

            /*
             * Uma interrupção observada antes do próximo poll impede
             * a criação de uma nova ProcessingRun.
             */
            if (Thread.currentThread()
                .isInterrupted()) {

                return;
            }

            trigger.execute(
                scheduleKey,
                schedulerInstanceId
            );

            /*
             * O shutdown pode ter sido solicitado enquanto o trigger
             * estava executando.
             *
             * Nesse caso não há motivo para entrar em nova espera.
             */
            if (!keepRunning.getAsBoolean()) {
                return;
            }

            if (!waitUntilNextPoll()) {
                return;
            }
        }
    }

    private boolean waitUntilNextPoll() {

        try {

            waitStrategy.await(
                pollInterval
            );

            return true;

        } catch (InterruptedException exception) {

            /*
             * InterruptedException limpa o flag da thread.
             * Restauramos o estado antes de encerrar.
             */
            Thread.currentThread()
                .interrupt();

            return false;
        }
    }

    private static Duration requirePositiveDuration(
        Duration duration
    ) {

        Objects.requireNonNull(
            duration,
            "pollInterval must not be null"
        );

        if (duration.isZero()
            || duration.isNegative()) {

            throw new IllegalArgumentException(
                "pollInterval must be positive"
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
