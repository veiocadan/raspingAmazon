package com.raspingamazon.application.orchestration.worker;

import com.raspingamazon.application.orchestration.ProcessingJob;

import java.util.Objects;
import java.util.function.LongConsumer;

/**
 * Dispatcher funcional dos jobs da FASE 12.
 *
 * <p>Cada tipo de job é associado a exatamente um handler:</p>
 *
 * <pre>
 * COLLECT_DEALS
 *     -> processingRunId
 *
 * ENRICH_DEAL
 *     -> dealCandidateId
 *
 * EVALUATE_DEAL
 *     -> offerSnapshotId
 * </pre>
 *
 * <p>O dispatcher conhece somente os identificadores dos sujeitos.
 * A composição da aplicação pode conectar esses handlers diretamente
 * aos respectivos casos de uso por method reference.</p>
 */
public final class DefaultProcessingJobExecutor
    implements ProcessingJobExecutionPort {

    private final LongConsumer collectDealsHandler;

    private final LongConsumer enrichDealHandler;

    private final LongConsumer evaluateDealHandler;

    public DefaultProcessingJobExecutor(
        LongConsumer collectDealsHandler,
        LongConsumer enrichDealHandler,
        LongConsumer evaluateDealHandler
    ) {

        this.collectDealsHandler =
            Objects.requireNonNull(
                collectDealsHandler,
                "collectDealsHandler must not be null"
            );

        this.enrichDealHandler =
            Objects.requireNonNull(
                enrichDealHandler,
                "enrichDealHandler must not be null"
            );

        this.evaluateDealHandler =
            Objects.requireNonNull(
                evaluateDealHandler,
                "evaluateDealHandler must not be null"
            );
    }

    @Override
    public void execute(
        ProcessingJob job
    ) {

        Objects.requireNonNull(
            job,
            "job must not be null"
        );

        switch (job.type()) {

            case COLLECT_DEALS ->
                collectDealsHandler.accept(
                    requireSubject(
                        job.processingRunId(),
                        "COLLECT_DEALS requires processingRunId"
                    )
                );

            case ENRICH_DEAL ->
                enrichDealHandler.accept(
                    requireSubject(
                        job.dealCandidateId(),
                        "ENRICH_DEAL requires dealCandidateId"
                    )
                );

            case EVALUATE_DEAL ->
                evaluateDealHandler.accept(
                    requireSubject(
                        job.offerSnapshotId(),
                        "EVALUATE_DEAL requires offerSnapshotId"
                    )
                );
        }
    }

    private long requireSubject(
        Long value,
        String message
    ) {

        if (value == null
            || value <= 0) {

            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
