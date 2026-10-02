package com.raspingamazon.application.orchestration.worker;

import com.raspingamazon.application.orchestration.ProcessingJob;

import java.util.Objects;
import java.util.function.LongConsumer;

/**
 * Dispatcher funcional dos jobs duráveis do pipeline.
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
 *
 * PUBLICATION_DISPATCH
 *     -> processingRunId
 * </pre>
 *
 * <p>O dispatcher conhece somente os identificadores dos sujeitos.
 * A composição da aplicação conecta esses handlers aos respectivos
 * casos de uso.</p>
 */
public final class DefaultProcessingJobExecutor
    implements ProcessingJobExecutionPort {

    private final LongConsumer collectDealsHandler;

    private final LongConsumer enrichDealHandler;

    private final LongConsumer evaluateDealHandler;

    private final LongConsumer publicationDispatchHandler;

    /**
     * Construtor de compatibilidade para composições que ainda não
     * ativaram PUBLICATION_DISPATCH.
     *
     * <p>Enquanto o novo job ainda não é enfileirado, o comportamento
     * dos três tipos anteriores permanece inalterado.</p>
     *
     * <p>Se um PUBLICATION_DISPATCH chegar acidentalmente a uma
     * composição antiga, a execução falha explicitamente em vez de
     * ignorar silenciosamente o trabalho.</p>
     */
    public DefaultProcessingJobExecutor(
        LongConsumer collectDealsHandler,
        LongConsumer enrichDealHandler,
        LongConsumer evaluateDealHandler
    ) {

        this(
            collectDealsHandler,
            enrichDealHandler,
            evaluateDealHandler,
            processingRunId -> {
                throw new IllegalStateException(
                    "PUBLICATION_DISPATCH handler is not configured"
                );
            }
        );
    }

    /**
     * Construtor completo com suporte à etapa de publicação automática.
     */
    public DefaultProcessingJobExecutor(
        LongConsumer collectDealsHandler,
        LongConsumer enrichDealHandler,
        LongConsumer evaluateDealHandler,
        LongConsumer publicationDispatchHandler
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

        this.publicationDispatchHandler =
            Objects.requireNonNull(
                publicationDispatchHandler,
                "publicationDispatchHandler must not be null"
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

            case PUBLICATION_DISPATCH ->
                publicationDispatchHandler.accept(
                    requireSubject(
                        job.processingRunId(),
                        "PUBLICATION_DISPATCH requires processingRunId"
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
