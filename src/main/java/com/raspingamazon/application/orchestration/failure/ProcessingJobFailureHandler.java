package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Aplica a política de falha a um ProcessingJob que já está em RUNNING.
 *
 * <p>Este componente não executa o job e não conhece coleta,
 * enriquecimento ou avaliação. Sua única responsabilidade é decidir
 * o próximo estado depois de uma falha:</p>
 *
 * <ul>
 *     <li>RETRY_WAIT para falha transitória quando ainda existem tentativas;</li>
 *     <li>DEAD para falha permanente;</li>
 *     <li>DEAD para falha transitória que esgotou as tentativas.</li>
 * </ul>
 */
public final class ProcessingJobFailureHandler {

    private final ProcessingFailureClassifier
        failureClassifier;

    private final RetryBackoffPolicy
        retryBackoffPolicy;

    private final ProcessingJobQueuePort
        jobQueue;

    public ProcessingJobFailureHandler(
        ProcessingFailureClassifier failureClassifier,
        RetryBackoffPolicy retryBackoffPolicy,
        ProcessingJobQueuePort jobQueue
    ) {

        this.failureClassifier =
            Objects.requireNonNull(
                failureClassifier,
                "failureClassifier must not be null"
            );

        this.retryBackoffPolicy =
            Objects.requireNonNull(
                retryBackoffPolicy,
                "retryBackoffPolicy must not be null"
            );

        this.jobQueue =
            Objects.requireNonNull(
                jobQueue,
                "jobQueue must not be null"
            );
    }

    /**
     * Registra o resultado de uma execução que falhou.
     *
     * @param job job já reclamado por um worker
     * @param workerId worker proprietário do lock
     * @param failure exceção produzida pela execução
     * @param failedAt instante em que a tentativa terminou
     * @return representação atualizada do job
     */
    public ProcessingJob handle(
        ProcessingJob job,
        String workerId,
        Throwable failure,
        OffsetDateTime failedAt
    ) {

        Objects.requireNonNull(
            job,
            "job must not be null"
        );

        requireText(
            workerId,
            "workerId must not be blank"
        );

        Objects.requireNonNull(
            failure,
            "failure must not be null"
        );

        Objects.requireNonNull(
            failedAt,
            "failedAt must not be null"
        );

        FailureClassification classification =
            failureClassifier.classify(
                failure
            );

        ProcessingFailure processingFailure =
            new ProcessingFailure(
                classification.type(),
                classification.code(),
                classification.message()
            );

        if (classification.type()
            == ProcessingFailureType.TRANSIENT
            && job.canRetry()) {

            OffsetDateTime availableAt =
                failedAt.plus(
                    retryBackoffPolicy.delayForAttempt(
                        job.attemptCount()
                    )
                );

            return jobQueue.scheduleRetry(
                job.id(),
                workerId,
                processingFailure,
                availableAt,
                failedAt
            );
        }

        return jobQueue.markDead(
            job.id(),
            workerId,
            processingFailure,
            failedAt
        );
    }

    private String requireText(
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
