package com.raspingamazon.application.orchestration.collection;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.deal.port.TransactionPort;
import com.raspingamazon.application.orchestration.DealCandidate;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingRun;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.orchestration.port.DealCandidateRepositoryPort;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.orchestration.port.ProcessingRunRepositoryPort;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.application.parsing.contract.ParsedDeal;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Caso de uso da etapa COLLECT_DEALS.
 *
 * <p>Responsabilidades:</p>
 *
 * <ol>
 *     <li>carregar a ProcessingRun;</li>
 *     <li>marcar a execução como RUNNING;</li>
 *     <li>executar coleta e parsing fora da transação JDBC;</li>
 *     <li>persistir DealCandidates;</li>
 *     <li>criar jobs ENRICH_DEAL;</li>
 *     <li>marcar a ProcessingRun como COMPLETED.</li>
 * </ol>
 *
 * <p>A etapa termina depois da criação durável dos candidatos e dos
 * jobs de enriquecimento. Nenhum enriquecimento é executado aqui.</p>
 *
 * <p>Uma ProcessingRun já COMPLETED representa uma execução lógica
 * concluída. Nesse caso, o método retorna sem executar novamente
 * coleta, parsing ou persistência.</p>
 */
public final class CollectDealsUseCase {

    private static final String FAILURE_CODE =
        "COLLECT_DEALS_FAILED";

    private final ProcessingRunRepositoryPort
        processingRunRepository;

    private final DealCandidateRepositoryPort
        dealCandidateRepository;

    private final ProcessingJobQueuePort
        processingJobQueue;

    private final CollectionCollector
        collectionCollector;

    private final DealsParser
        dealsParser;

    private final TransactionPort
        transactionPort;

    private final Clock
        clock;

    private final int
        enrichmentMaxAttempts;

    public CollectDealsUseCase(
        ProcessingRunRepositoryPort processingRunRepository,
        DealCandidateRepositoryPort dealCandidateRepository,
        ProcessingJobQueuePort processingJobQueue,
        CollectionCollector collectionCollector,
        DealsParser dealsParser,
        TransactionPort transactionPort,
        Clock clock,
        int enrichmentMaxAttempts
    ) {

        this.processingRunRepository =
            Objects.requireNonNull(
                processingRunRepository,
                "processingRunRepository must not be null"
            );

        this.dealCandidateRepository =
            Objects.requireNonNull(
                dealCandidateRepository,
                "dealCandidateRepository must not be null"
            );

        this.processingJobQueue =
            Objects.requireNonNull(
                processingJobQueue,
                "processingJobQueue must not be null"
            );

        this.collectionCollector =
            Objects.requireNonNull(
                collectionCollector,
                "collectionCollector must not be null"
            );

        this.dealsParser =
            Objects.requireNonNull(
                dealsParser,
                "dealsParser must not be null"
            );

        this.transactionPort =
            Objects.requireNonNull(
                transactionPort,
                "transactionPort must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        if (enrichmentMaxAttempts <= 0) {
            throw new IllegalArgumentException(
                "enrichmentMaxAttempts must be positive"
            );
        }

        this.enrichmentMaxAttempts =
            enrichmentMaxAttempts;
    }

    /**
     * Executa uma ProcessingRun existente.
     *
     * @param processingRunId identidade persistente da run
     * @return estado COMPLETED da execução
     */
    public ProcessingRun execute(
        long processingRunId
    ) {

        if (processingRunId <= 0) {
            throw new IllegalArgumentException(
                "processingRunId must be positive"
            );
        }

        ProcessingRun run =
            processingRunRepository.findById(
                    processingRunId
                )
                .orElseThrow(
                    () -> new IllegalArgumentException(
                        "ProcessingRun not found: "
                            + processingRunId
                    )
                );

        /*
         * Idempotência da etapa completa.
         *
         * É possível que a transação da coleta tenha terminado com
         * sucesso e a JVM tenha caído antes de o worker marcar o
         * ProcessingJob como SUCCEEDED.
         *
         * Nesse cenário, reexecutar coleta e parsing seria trabalho
         * externo desnecessário.
         */
        if (run.status()
            == ProcessingRunStatus.COMPLETED) {

            return run;
        }

        /*
         * RUNNING também é aceito.
         *
         * Esse estado pode sobreviver a uma interrupção da JVM
         * ocorrida depois de markRunning() e antes da conclusão da
         * etapa. O ProcessingJob controla exclusividade entre workers.
         */
        if (run.status()
            != ProcessingRunStatus.PENDING
            && run.status()
            != ProcessingRunStatus.FAILED
            && run.status()
            != ProcessingRunStatus.RUNNING) {

            throw new IllegalStateException(
                "Unsupported ProcessingRun status for collection: "
                    + run.status()
            );
        }

        ProcessingRun runningRun =
            ensureRunning(
                run
            );

        try {

            CollectionResult collectionResult =
                collectionCollector.collect(
                    new CollectionRequest(
                        runningRun.source()
                    )
                );

            List<ParsedDeal> parsedDeals =
                Objects.requireNonNull(
                    dealsParser.parse(
                        collectionResult
                    ),
                    "dealsParser must not return null"
                );

            OffsetDateTime completedAt =
                OffsetDateTime.now(
                    clock
                );

            return transactionPort.execute(
                () -> persistCollectionResult(
                    runningRun.id(),
                    parsedDeals,
                    completedAt
                )
            );

        } catch (RuntimeException exception) {

            markFailedSafely(
                runningRun.id(),
                exception
            );

            throw exception;
        }
    }

    /**
     * Coloca a run em RUNNING quando ela ainda estiver PENDING
     * ou FAILED.
     *
     * <p>RUNNING é mantido sem alteração para permitir reentrada
     * após interrupção anterior.</p>
     */
    private ProcessingRun ensureRunning(
        ProcessingRun run
    ) {

        if (run.status()
            == ProcessingRunStatus.RUNNING) {

            return run;
        }

        OffsetDateTime startedAt =
            OffsetDateTime.now(
                clock
            );

        return transactionPort.execute(
            () -> processingRunRepository.markRunning(
                run.id(),
                startedAt
            )
        );
    }

    /**
     * Persiste atomicamente o resultado durável da coleta.
     *
     * <p>Candidate + ENRICH_DEAL precisam pertencer à mesma
     * transação. Não podemos persistir um candidato sem deixar
     * trabalho correspondente disponível para processamento.</p>
     */
    private ProcessingRun persistCollectionResult(
        long processingRunId,
        List<ParsedDeal> parsedDeals,
        OffsetDateTime completedAt
    ) {

        for (ParsedDeal parsedDeal : parsedDeals) {

            Objects.requireNonNull(
                parsedDeal,
                "parsedDeals must not contain null"
            );

            DealCandidate candidate =
                dealCandidateRepository.save(
                    new DealCandidate(
                        null,
                        processingRunId,
                        parsedDeal
                    )
                );

            Long candidateId =
                candidate.id();

            if (candidateId == null) {
                throw new IllegalStateException(
                    "Persisted DealCandidate must have an id"
                );
            }

            processingJobQueue.enqueue(
                ProcessingJobSubmission.enrichDeal(
                    candidateId,
                    enrichmentIdempotencyKey(
                        candidateId
                    ),
                    enrichmentMaxAttempts,
                    completedAt
                )
            );
        }

        return processingRunRepository.markCompleted(
            processingRunId,
            completedAt
        );
    }

    /**
     * Registra a falha lógica da run em uma nova fronteira
     * transacional.
     *
     * <p>Se a falha ocorreu durante a transação que persistia
     * candidatos e jobs, aquela transação já terá sido revertida
     * pela TransactionPort antes desta chamada.</p>
     */
    private void markFailedSafely(
        long processingRunId,
        RuntimeException originalFailure
    ) {

        OffsetDateTime failedAt =
            OffsetDateTime.now(
                clock
            );

        try {

            transactionPort.execute(
                () -> processingRunRepository.markFailed(
                    processingRunId,
                    FAILURE_CODE,
                    originalFailure.getMessage(),
                    failedAt
                )
            );

        } catch (RuntimeException failureWhileMarkingFailed) {

            /*
             * A falha original continua sendo a informação principal.
             * A eventual falha ao registrar FAILED é preservada como
             * suppressed para diagnóstico.
             */
            originalFailure.addSuppressed(
                failureWhileMarkingFailed
            );
        }
    }

    private String enrichmentIdempotencyKey(
        long candidateId
    ) {

        return "enrich:"
            + candidateId;
    }
}
