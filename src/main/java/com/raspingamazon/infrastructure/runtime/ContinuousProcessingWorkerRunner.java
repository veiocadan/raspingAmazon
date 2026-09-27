package com.raspingamazon.infrastructure.runtime;

import com.raspingamazon.application.orchestration.worker.ProcessingWorker;
import com.raspingamazon.application.orchestration.worker.ProcessingWorkerRunResult;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Runtime operacional que transforma ProcessingWorker.runOnce()
 * em processamento contínuo.
 *
 * <p>Este componente não conhece:</p>
 *
 * <ul>
 *     <li>ProcessingSchedule;</li>
 *     <li>Amazon;</li>
 *     <li>coleta;</li>
 *     <li>parser;</li>
 *     <li>regras de negócio;</li>
 *     <li>retry de jobs;</li>
 *     <li>PostgreSQL.</li>
 * </ul>
 *
 * <p>Seu único papel é decidir quando executar uma nova rodada do
 * ProcessingWorker.</p>
 *
 * <p>Quando uma rodada processa um job, a próxima rodada começa
 * imediatamente para permitir que o worker drene trabalho já
 * disponível.</p>
 *
 * <p>Quando nenhuma unidade está disponível, o runtime aguarda o
 * idleDelay configurado antes de consultar novamente.</p>
 *
 * <p>Uma interrupção durante a espera encerra o loop e preserva o
 * estado de interrupção da thread.</p>
 *
 * <p>RuntimeExceptions produzidas pelo ProcessingWorker não são
 * engolidas. Falhas inesperadas da infraestrutura devem permanecer
 * observáveis. Políticas mais amplas de recuperação pertencem à
 * FASE 20.</p>
 */
public final class ContinuousProcessingWorkerRunner {

    private final ProcessingWorker worker;

    private final Duration idleDelay;

    private final WorkerIdleWaitStrategy idleWaitStrategy;

    public ContinuousProcessingWorkerRunner(
        ProcessingWorker worker,
        Duration idleDelay,
        WorkerIdleWaitStrategy idleWaitStrategy
    ) {

        this.worker =
            Objects.requireNonNull(
                worker,
                "worker must not be null"
            );

        this.idleDelay =
            requirePositiveDuration(
                idleDelay,
                "idleDelay"
            );

        this.idleWaitStrategy =
            Objects.requireNonNull(
                idleWaitStrategy,
                "idleWaitStrategy must not be null"
            );
    }

    /**
     * Executa o worker continuamente enquanto o sinal informado
     * permanecer ativo.
     *
     * <p>O BooleanSupplier permite que a futura composição de runtime
     * controle shutdown sem acoplar esta classe a hooks da JVM,
     * executors ou sinais do sistema operacional.</p>
     *
     * @param keepRunning sinal de continuidade
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
             * Caso a interrupção tenha ocorrido fora do idle sleep,
             * não iniciamos outra unidade de trabalho.
             */
            if (Thread.currentThread()
                .isInterrupted()) {

                return;
            }

            ProcessingWorkerRunResult result =
                worker.runOnce();

            /*
             * Trabalho disponível:
             *
             * tenta imediatamente a próxima unidade.
             *
             * Não existe sleep artificial entre jobs que já estão
             * disponíveis na fila.
             */
            if (result.jobClaimed()) {
                continue;
            }

            /*
             * Fila momentaneamente vazia.
             *
             * Somente neste caso existe polling com espera.
             */
            if (!waitUntilNextPoll()) {
                return;
            }
        }
    }

    /**
     * Aguarda a próxima consulta.
     *
     * @return true quando o loop pode continuar;
     *         false quando a thread foi interrompida
     */
    private boolean waitUntilNextPoll() {

        try {

            idleWaitStrategy.await(
                idleDelay
            );

            return true;

        } catch (InterruptedException exception) {

            /*
             * InterruptedException limpa o flag de interrupção.
             *
             * Restauramos o estado antes de encerrar para que camadas
             * externas possam observar corretamente que a thread foi
             * interrompida.
             */
            Thread.currentThread()
                .interrupt();

            return false;
        }
    }

    private static Duration requirePositiveDuration(
        Duration duration,
        String name
    ) {

        Objects.requireNonNull(
            duration,
            name + " must not be null"
        );

        if (duration.isZero()
            || duration.isNegative()) {

            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }

        return duration;
    }
}
