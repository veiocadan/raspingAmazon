package com.raspingamazon.application.orchestration.worker;

import com.raspingamazon.application.orchestration.ProcessingJob;
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
 *     <li>aplica a política de falhas em caso de RuntimeException.</li>
 * </ol>
 *
 * <p>Este componente deliberadamente não implementa loop infinito,
 * scheduler ou sleep. O mecanismo que decide quando chamar runOnce()
 * pertence à composição operacional.</p>
 */
public final class ProcessingWorker {

    private final String workerId;

    private final ProcessingJobQueuePort
        jobQueue;

    private final ProcessingJobExecutionPort
        jobExecutionPort;

    private final ProcessingJobFailureHandler
        failureHandler;

    private final Clock clock;

    public ProcessingWorker(
        String workerId,
        ProcessingJobQueuePort jobQueue,
        ProcessingJobExecutionPort jobExecutionPort,
        ProcessingJobFailureHandler failureHandler,
        Clock clock
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

        return ProcessingWorkerRunResult.completed(
            requirePersistedId(
                succeededJob
            ),
            succeededJob.status()
        );
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
