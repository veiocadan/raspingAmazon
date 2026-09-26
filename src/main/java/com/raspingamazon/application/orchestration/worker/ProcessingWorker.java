package com.raspingamazon.application.orchestration.worker;

import com.raspingamazon.application.observability.OperationalLogContext;
import com.raspingamazon.application.observability.OperationalLogEvent;
import com.raspingamazon.application.observability.OperationalLogLevel;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.failure.ProcessingJobFailureHandler;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Executa uma unidade de trabalho por rodada.
 *
 * <p>O worker:</p>
 *
 * <ol>
 *     <li>reivindica atomicamente um job;</li>
 *     <li>executa o estágio correspondente;</li>
 *     <li>marca SUCCEEDED em caso de sucesso;</li>
 *     <li>aplica a política de falhas em caso de RuntimeException;</li>
 *     <li>emite eventos operacionais estruturados sobre o ciclo do job.</li>
 * </ol>
 *
 * <p>Este componente deliberadamente não implementa loop infinito,
 * scheduler ou sleep. O mecanismo que decide quando chamar runOnce()
 * pertence à composição operacional.</p>
 *
 * <p>A observabilidade é complementar ao processamento. Uma falha na
 * emissão de um log não pode alterar o estado, retry ou resultado do
 * job.</p>
 */
public final class ProcessingWorker {

    private static final String LOG_COMPONENT =
        "processing-worker";

    private static final String LOG_OPERATION =
        "execute-job";

    private final String workerId;

    private final ProcessingJobQueuePort
        jobQueue;

    private final ProcessingJobExecutionPort
        jobExecutionPort;

    private final ProcessingJobFailureHandler
        failureHandler;

    private final Clock clock;

    private final StructuredOperationalLogPort
        operationalLog;

    /**
     * Construtor compatível com as composições existentes.
     *
     * <p>Nesta forma, nenhum destino de log é configurado. A variante
     * que recebe StructuredOperationalLogPort deve ser utilizada pela
     * composição operacional que desejar emitir observabilidade
     * estruturada.</p>
     */
    public ProcessingWorker(
        String workerId,
        ProcessingJobQueuePort jobQueue,
        ProcessingJobExecutionPort jobExecutionPort,
        ProcessingJobFailureHandler failureHandler,
        Clock clock
    ) {

        this(
            workerId,
            jobQueue,
            jobExecutionPort,
            failureHandler,
            clock,
            event -> {
            }
        );
    }

    /**
     * Construtor com observabilidade estruturada explícita.
     */
    public ProcessingWorker(
        String workerId,
        ProcessingJobQueuePort jobQueue,
        ProcessingJobExecutionPort jobExecutionPort,
        ProcessingJobFailureHandler failureHandler,
        Clock clock,
        StructuredOperationalLogPort operationalLog
    ) {

        this.workerId =
            requireText(
                workerId,
                "workerId must not be blank"
            );

        this.jobQueue =
            Objects.requireNonNull(
                jobQueue,
                "jobQueue must not be null"
            );

        this.jobExecutionPort =
            Objects.requireNonNull(
                jobExecutionPort,
                "jobExecutionPort must not be null"
            );

        this.failureHandler =
            Objects.requireNonNull(
                failureHandler,
                "failureHandler must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        this.operationalLog =
            Objects.requireNonNull(
                operationalLog,
                "operationalLog must not be null"
            );
    }

    public ProcessingWorkerRunResult runOnce() {

        OffsetDateTime claimedAt =
            OffsetDateTime.now(
                clock
            );

        Optional<ProcessingJob> claimed =
            jobQueue.claimNext(
                workerId,
                claimedAt
            );

        if (claimed.isEmpty()) {
            return ProcessingWorkerRunResult.idle();
        }

        ProcessingJob job =
            claimed.get();

        emit(
            job,
            OperationalLogLevel.INFO,
            "processing.job.claimed",
            "CLAIMED"
        );

        try {

            jobExecutionPort.execute(
                job
            );

        } catch (RuntimeException failure) {

            OffsetDateTime failedAt =
                OffsetDateTime.now(
                    clock
                );

            ProcessingJob failedJob =
                failureHandler.handle(
                    job,
                    workerId,
                    failure,
                    failedAt
                );

            emitFailureOutcome(
                failedJob
            );

            return ProcessingWorkerRunResult.completed(
                requirePersistedId(
                    failedJob
                ),
                failedJob.status()
            );
        }

        /*
         * markSucceeded permanece fora do catch da execução.
         *
         * Isso é importante para concorrência:
         * caso o lease tenha expirado enquanto o trabalho estava
         * executando, o worker antigo não deve interpretar uma falha
         * de ownership no ACK como falha funcional e tentar reagendar
         * novamente o job.
         */
        OffsetDateTime finishedAt =
            OffsetDateTime.now(
                clock
            );

        ProcessingJob succeededJob =
            jobQueue.markSucceeded(
                requirePersistedId(
                    job
                ),
                workerId,
                finishedAt
            );

        emit(
            succeededJob,
            OperationalLogLevel.INFO,
            "processing.job.succeeded",
            "SUCCEEDED"
        );

        return ProcessingWorkerRunResult.completed(
            requirePersistedId(
                succeededJob
            ),
            succeededJob.status()
        );
    }

    private void emitFailureOutcome(
        ProcessingJob failedJob
    ) {

        if (failedJob.status()
            == ProcessingJobStatus.RETRY_WAIT) {

            emit(
                failedJob,
                OperationalLogLevel.WARN,
                "processing.job.retry-scheduled",
                "RETRY_WAIT"
            );

            return;
        }

        if (failedJob.status()
            == ProcessingJobStatus.DEAD) {

            emit(
                failedJob,
                OperationalLogLevel.ERROR,
                "processing.job.dead",
                "DEAD"
            );

            return;
        }

        /*
         * ProcessingJobFailureHandler atualmente só deve retornar
         * RETRY_WAIT ou DEAD.
         *
         * Caso outra implementação futura devolva outro estado, o
         * worker não altera sua semântica por causa do logging.
         */
        emit(
            failedJob,
            OperationalLogLevel.WARN,
            "processing.job.failure-handled",
            failedJob.status()
                .name()
        );
    }

    /**
     * Emite observabilidade sem permitir que ela altere a execução
     * funcional do job.
     *
     * <p>O contexto utiliza somente identidades já presentes no job.
     * Nenhuma consulta adicional ao banco é executada apenas para
     * enriquecer um log.</p>
     *
     * <p>Metadados de classificação da falha não são preenchidos
     * neste nível. O worker conhece o resultado operacional do job,
     * mas não possui contexto suficiente para determinar com segurança
     * se a origem concreta da falha foi EXTERNAL ou INTERNAL.</p>
     *
     * <p>A observação completa de failureOrigin, failureType,
     * errorCode e durationMs pertence às fronteiras concretas de
     * integração.</p>
     */
    private void emit(
        ProcessingJob job,
        OperationalLogLevel level,
        String event,
        String outcome
    ) {

        try {

            OperationalLogContext context =
                new OperationalLogContext(
                    job.processingRunId(),
                    job.id(),
                    job.type(),
                    job.dealCandidateId(),
                    job.offerSnapshotId(),
                    null,
                    null,
                    null,
                    null
                );

            OperationalLogEvent logEvent =
                new OperationalLogEvent(
                    level,
                    event,
                    LOG_COMPONENT,
                    LOG_OPERATION,
                    context,
                    outcome,
                    null,
                    null,
                    null,
                    null
                );

            operationalLog.log(
                logEvent
            );

        } catch (RuntimeException ignored) {

            /*
             * Observabilidade é complementar.
             *
             * Uma falha no destino de logging, na serialização ou na
             * construção do evento não pode:
             *
             * - impedir a execução do job;
             * - transformar sucesso em falha;
             * - criar retry;
             * - transformar retry em DEAD.
             *
             * Não existe logging recursivo aqui porque isso criaria
             * uma nova dependência da própria infraestrutura que
             * acabou de falhar.
             */
        }
    }

    private long requirePersistedId(
        ProcessingJob job
    ) {

        Long id =
            job.id();

        if (id == null
            || id <= 0) {

            throw new IllegalStateException(
                "Claimed ProcessingJob must have a persisted id"
            );
        }

        return id;
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
