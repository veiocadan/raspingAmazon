package com.raspingamazon.infrastructure.runtime;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Lifecycle do processamento contínuo.
 *
 * <p>Este componente coordena exatamente dois loops:</p>
 *
 * <pre>
 * scheduler thread
 *       +
 * worker thread
 * </pre>
 *
 * <p>O runtime não conhece PostgreSQL, Amazon, jobs, schedules,
 * coleta, parsing ou regras de negócio.</p>
 *
 * <p>Suas responsabilidades são exclusivamente operacionais:</p>
 *
 * <ul>
 *     <li>iniciar scheduler e worker em threads independentes;</li>
 *     <li>fornecer o mesmo sinal de continuidade aos dois loops;</li>
 *     <li>propagar pedido de shutdown;</li>
 *     <li>acordar loops bloqueados em espera por meio de interrupt;</li>
 *     <li>encerrar o outro loop quando um deles falhar;</li>
 *     <li>preservar a causa da primeira falha terminal;</li>
 *     <li>permitir espera explícita pela terminação completa.</li>
 * </ul>
 *
 * <p>As threads não são daemon. Enquanto o runtime estiver ativo,
 * elas representam trabalho operacional real do processo.</p>
 *
 * <p>Este componente não fecha Connections JDBC. O lifecycle dos
 * recursos pertence ao bootstrap que os abriu.</p>
 */
public final class ContinuousProcessingRuntime
    implements AutoCloseable {

    private static final String SCHEDULER_THREAD_NAME =
        "rasping-amazon-scheduler";

    private static final String WORKER_THREAD_NAME =
        "rasping-amazon-worker";

    private final Consumer<BooleanSupplier>
        schedulerLoop;

    private final Consumer<BooleanSupplier>
        workerLoop;

    private final AtomicBoolean
        started =
        new AtomicBoolean();

    private final AtomicBoolean
        shutdownRequested =
        new AtomicBoolean();

    private final AtomicBoolean
        keepRunning =
        new AtomicBoolean();

    private final AtomicReference<Throwable>
        terminalFailure =
        new AtomicReference<>();

    private final CountDownLatch
        terminated =
        new CountDownLatch(
            2
        );

    private volatile Thread
        schedulerThread;

    private volatile Thread
        workerThread;

    /**
     * Construtor de produção.
     *
     * @param schedulerRunner runner contínuo do scheduler
     * @param workerRunner runner contínuo do worker
     */
    public ContinuousProcessingRuntime(
        ContinuousProcessingSchedulerRunner schedulerRunner,
        ContinuousProcessingWorkerRunner workerRunner
    ) {

        this(
            requireScheduler(
                schedulerRunner
            )::run,
            requireWorker(
                workerRunner
            )::run
        );
    }

    /**
     * Fronteira interna utilizada para manter o lifecycle testável
     * independentemente de banco, fila ou fonte externa.
     */
    ContinuousProcessingRuntime(
        Consumer<BooleanSupplier> schedulerLoop,
        Consumer<BooleanSupplier> workerLoop
    ) {

        this.schedulerLoop =
            Objects.requireNonNull(
                schedulerLoop,
                "schedulerLoop must not be null"
            );

        this.workerLoop =
            Objects.requireNonNull(
                workerLoop,
                "workerLoop must not be null"
            );
    }

    /**
     * Inicia scheduler e worker.
     *
     * <p>Uma instância deste runtime é single-use. Depois de iniciada
     * ou encerrada, não pode ser iniciada novamente.</p>
     */
    public void start() {

        if (shutdownRequested.get()) {
            throw new IllegalStateException(
                "Continuous processing runtime was already shut down"
            );
        }

        if (!started.compareAndSet(
            false,
            true
        )) {

            throw new IllegalStateException(
                "Continuous processing runtime was already started"
            );
        }

        keepRunning.set(
            true
        );

        schedulerThread =
            new Thread(
                () ->
                    executeLoop(
                        "scheduler",
                        schedulerLoop
                    ),
                SCHEDULER_THREAD_NAME
            );

        workerThread =
            new Thread(
                () ->
                    executeLoop(
                        "worker",
                        workerLoop
                    ),
                WORKER_THREAD_NAME
            );

        /*
         * As duas Threads são construídas antes de qualquer uma ser
         * iniciada. Assim uma falha muito precoce já consegue sinalizar
         * a outra participante do lifecycle.
         */
        workerThread.start();
        schedulerThread.start();
    }

    /**
     * Solicita encerramento coordenado.
     *
     * <p>O método é idempotente.</p>
     *
     * <p>Primeiro o sinal compartilhado passa para false. Em seguida
     * as threads são interrompidas para acordar sleeps/polls que estejam
     * aguardando o próximo ciclo.</p>
     *
     * <p>Se uma thread estiver executando trabalho real no instante do
     * interrupt, a própria camada de processamento continua responsável
     * por preservar sua semântica durável.</p>
     */
    public void requestShutdown() {

        shutdownRequested.set(
            true
        );

        stopLoops();
    }

    /**
     * Aguarda a terminação dos dois loops.
     *
     * <p>Quando uma das threads tiver terminado anormalmente, a primeira
     * causa observada é lançada depois que ambos os loops terminarem.</p>
     *
     * @throws InterruptedException quando a thread chamadora for
     *                              interrompida durante a espera
     */
    public void awaitTermination()
        throws InterruptedException {

        requireStarted();

        rejectRuntimeThreadWait();

        terminated.await();

        Throwable failure =
            terminalFailure.get();

        if (failure != null) {
            throw new ContinuousProcessingRuntimeException(
                "Continuous processing runtime terminated with failure",
                failure
            );
        }
    }

    /**
     * Indica se o sinal operacional continua ativo.
     */
    public boolean isRunning() {

        return started.get()
            && keepRunning.get()
            && terminated.getCount() > 0;
    }

    /**
     * Indica se o shutdown explícito já foi solicitado.
     */
    public boolean shutdownRequested() {

        return shutdownRequested.get();
    }

    /**
     * Encerra o runtime e espera os loops terminarem.
     *
     * <p>close() é idempotente e pode ser utilizado por shutdown hook
     * ou try-with-resources.</p>
     *
     * <p>Caso a thread chamadora seja interrompida durante a espera,
     * continuamos aguardando o término coordenado e restauramos o flag
     * de interrupção ao final.</p>
     */
    @Override
    public void close() {

        requestShutdown();

        if (!started.get()) {
            return;
        }

        /*
         * Uma das próprias threads do runtime não pode esperar por si
         * mesma. Nesse cenário o pedido de parada já foi realizado e
         * o método pode retornar.
         */
        if (Thread.currentThread()
            == schedulerThread
            || Thread.currentThread()
            == workerThread) {

            return;
        }

        boolean interrupted =
            false;

        while (true) {

            try {

                terminated.await();
                break;

            } catch (InterruptedException exception) {

                interrupted =
                    true;

                /*
                 * InterruptedException limpa o flag. Continuamos a
                 * aguardar o shutdown completo e restauramos o estado
                 * somente ao final.
                 */
                stopLoops();
            }
        }

        if (interrupted) {
            Thread.currentThread()
                .interrupt();
        }
    }

    private void executeLoop(
        String loopName,
        Consumer<BooleanSupplier> loop
    ) {

        try {

            loop.accept(
                keepRunning::get
            );

            /*
             * Se o loop retornou enquanto o runtime ainda estava ativo,
             * houve uma terminação operacional inesperada.
             *
             * Um processo contínuo não deve permanecer parcialmente
             * ativo somente com scheduler ou somente com worker.
             */
            if (keepRunning.get()) {

                terminalFailure.compareAndSet(
                    null,
                    new IllegalStateException(
                        loopName
                            + " loop terminated while runtime was active"
                    )
                );

                stopLoops();
            }

        } catch (Throwable failure) {

            /*
             * Guardamos somente a primeira causa.
             *
             * A falha secundária do outro loop durante o shutdown não
             * deve esconder a origem que iniciou a terminação.
             */
            terminalFailure.compareAndSet(
                null,
                failure
            );

            stopLoops();

        } finally {

            terminated.countDown();
        }
    }

    private void stopLoops() {

        keepRunning.set(
            false
        );

        interruptIfAlive(
            schedulerThread
        );

        interruptIfAlive(
            workerThread
        );
    }

    private void interruptIfAlive(
        Thread thread
    ) {

        if (thread == null) {
            return;
        }

        /*
         * Não interrompemos a thread chamadora a partir daqui.
         *
         * Quando a própria thread detecta uma falha, não existe motivo
         * para adicionar artificialmente um interrupt flag nela antes
         * de seu finally.
         */
        if (thread
            == Thread.currentThread()) {

            return;
        }

        if (thread.isAlive()) {
            thread.interrupt();
        }
    }

    private void requireStarted() {

        if (!started.get()) {
            throw new IllegalStateException(
                "Continuous processing runtime has not been started"
            );
        }
    }

    private void rejectRuntimeThreadWait() {

        Thread current =
            Thread.currentThread();

        if (current
            == schedulerThread
            || current
            == workerThread) {

            throw new IllegalStateException(
                "A runtime thread cannot await its own termination"
            );
        }
    }

    private static ContinuousProcessingSchedulerRunner
    requireScheduler(
        ContinuousProcessingSchedulerRunner runner
    ) {

        return Objects.requireNonNull(
            runner,
            "schedulerRunner must not be null"
        );
    }

    private static ContinuousProcessingWorkerRunner
    requireWorker(
        ContinuousProcessingWorkerRunner runner
    ) {

        return Objects.requireNonNull(
            runner,
            "workerRunner must not be null"
        );
    }
}
