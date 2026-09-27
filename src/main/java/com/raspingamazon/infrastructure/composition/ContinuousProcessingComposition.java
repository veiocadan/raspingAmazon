package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.deal.OfferSnapshotFactory;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.evaluation.AmazonDealEvaluationApplicationService;
import com.raspingamazon.application.momentum.MomentumCalculationService;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;
import com.raspingamazon.application.orchestration.collection.CollectDealsUseCase;
import com.raspingamazon.application.orchestration.enrichment.EnrichDealUseCase;
import com.raspingamazon.application.orchestration.evaluation.EvaluateDealUseCase;
import com.raspingamazon.application.orchestration.failure.DefaultProcessingFailureClassifier;
import com.raspingamazon.application.orchestration.failure.ExponentialRetryBackoffPolicy;
import com.raspingamazon.application.orchestration.failure.ProcessingJobFailureHandler;
import com.raspingamazon.application.orchestration.worker.DefaultProcessingJobExecutor;
import com.raspingamazon.application.orchestration.worker.ProcessingWorker;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.application.scheduling.ScheduleProcessingRunUseCase;
import com.raspingamazon.application.scheduling.ScheduledProcessingTrigger;
import com.raspingamazon.domain.filter.BestCashDiscountSelector;
import com.raspingamazon.domain.filter.CommercialFilterEngine;
import com.raspingamazon.domain.history.SnapshotEvolutionCalculator;
import com.raspingamazon.domain.momentum.MomentumEngine;
import com.raspingamazon.domain.scoring.ScoreEngine;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.infrastructure.persistence.DealEvaluationJdbcRepository;
import com.raspingamazon.infrastructure.persistence.FilterProfileJdbcRepository;
import com.raspingamazon.infrastructure.persistence.MomentumAuditJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferEvidenceJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferHistoryJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferPaymentConditionRepository;
import com.raspingamazon.infrastructure.persistence.OfferSnapshotRepository;
import com.raspingamazon.infrastructure.persistence.ProductRepository;
import com.raspingamazon.infrastructure.persistence.ScoreProfileJdbcRepository;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcDealCandidateRepositoryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcDealEvaluationLookupAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcEnrichedOfferLookupAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcOfferSnapshotEvaluationLoadAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcOfferSnapshotEvaluationLockAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingJobQueueAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingRunRepositoryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingScheduleAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcTransactionAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.OfferEvidenceJdbcPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.OfferSnapshotJdbcPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.PaymentConditionJdbcPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.ProductJdbcPersistenceAdapter;
import com.raspingamazon.infrastructure.runtime.ContinuousProcessingSchedulerRunner;
import com.raspingamazon.infrastructure.runtime.ContinuousProcessingWorkerRunner;
import com.raspingamazon.infrastructure.runtime.ProcessingSchedulerWaitStrategy;
import com.raspingamazon.infrastructure.runtime.WorkerIdleWaitStrategy;

import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Composition root da execução contínua do processamento.
 *
 * <p>Esta composição une dois fluxos operacionais independentes:</p>
 *
 * <pre>
 * scheduler
 *     ↓
 * ProcessingRun
 *     ↓
 * COLLECT_DEALS
 *
 * worker
 *     ↓
 * ProcessingJob
 *     ↓
 * COLLECT_DEALS
 * ENRICH_DEAL
 * EVALUATE_DEAL
 * </pre>
 *
 * <p>O scheduler não executa coleta, parsing, enrichment ou avaliação.
 * Sua responsabilidade termina na criação idempotente da ProcessingRun
 * e do primeiro job durável.</p>
 *
 * <p>O worker reutiliza integralmente a orquestração da FASE 12.</p>
 *
 * <p>Scheduler e worker recebem Connections JDBC diferentes porque os
 * dois runners poderão executar concorrentemente quando o lifecycle
 * do processo for implementado.</p>
 *
 * <p>A composição deliberadamente não:</p>
 *
 * <ul>
 *     <li>cria Thread;</li>
 *     <li>cria ExecutorService;</li>
 *     <li>registra shutdown hook;</li>
 *     <li>inicia os runners;</li>
 *     <li>fecha Connections;</li>
 *     <li>implementa retry adicional;</li>
 *     <li>implementa regras específicas do scheduler dentro do worker.</li>
 * </ul>
 */
public final class ContinuousProcessingComposition {

    private final ContinuousProcessingSchedulerRunner
        schedulerRunner;

    private final ContinuousProcessingWorkerRunner
        workerRunner;

    /**
     * Monta o grafo de execução contínua.
     *
     * <p>As fronteiras externas de coleta, parsing e enrichment são
     * recebidas prontas. Isso permite que a composição operacional use
     * a implementação Amazon atual sem duplicar regras de aquisição.</p>
     *
     * @param schedulerConnection conexão exclusiva do scheduler
     * @param workerConnection conexão exclusiva do worker
     * @param collectionCollector fronteira de coleta
     * @param dealsParser parser da origem coletada
     * @param enrichmentClient fronteira de enrichment
     * @param operationalLog logging estruturado do worker
     * @param clock relógio compartilhado
     * @param schedulerWaitStrategy estratégia de espera do scheduler
     * @param workerIdleWaitStrategy estratégia de espera do worker
     * @param settings configuração operacional da composição
     */
    public ContinuousProcessingComposition(
        Connection schedulerConnection,
        Connection workerConnection,
        CollectionCollector collectionCollector,
        DealsParser dealsParser,
        ProductEnrichmentClient enrichmentClient,
        StructuredOperationalLogPort operationalLog,
        Clock clock,
        ProcessingSchedulerWaitStrategy schedulerWaitStrategy,
        WorkerIdleWaitStrategy workerIdleWaitStrategy,
        Settings settings
    ) {

        Objects.requireNonNull(
            schedulerConnection,
            "schedulerConnection must not be null"
        );

        Objects.requireNonNull(
            workerConnection,
            "workerConnection must not be null"
        );

        /*
         * Não usamos equals() aqui.
         *
         * O contrato é impedir que a MESMA instância física de
         * Connection seja entregue aos dois loops concorrentes.
         */
        if (schedulerConnection
            == workerConnection) {

            throw new IllegalArgumentException(
                "schedulerConnection and workerConnection "
                    + "must be distinct instances"
            );
        }

        Objects.requireNonNull(
            collectionCollector,
            "collectionCollector must not be null"
        );

        Objects.requireNonNull(
            dealsParser,
            "dealsParser must not be null"
        );

        Objects.requireNonNull(
            enrichmentClient,
            "enrichmentClient must not be null"
        );

        Objects.requireNonNull(
            operationalLog,
            "operationalLog must not be null"
        );

        Objects.requireNonNull(
            clock,
            "clock must not be null"
        );

        Objects.requireNonNull(
            schedulerWaitStrategy,
            "schedulerWaitStrategy must not be null"
        );

        Objects.requireNonNull(
            workerIdleWaitStrategy,
            "workerIdleWaitStrategy must not be null"
        );

        Objects.requireNonNull(
            settings,
            "settings must not be null"
        );

        this.schedulerRunner =
            createSchedulerRunner(
                schedulerConnection,
                clock,
                schedulerWaitStrategy,
                settings
            );

        this.workerRunner =
            createWorkerRunner(
                workerConnection,
                collectionCollector,
                dealsParser,
                enrichmentClient,
                operationalLog,
                clock,
                workerIdleWaitStrategy,
                settings
            );
    }

    /**
     * Runner responsável somente pelo polling do schedule.
     */
    public ContinuousProcessingSchedulerRunner
    schedulerRunner() {

        return schedulerRunner;
    }

    /**
     * Runner responsável somente pelo consumo contínuo da fila.
     */
    public ContinuousProcessingWorkerRunner
    workerRunner() {

        return workerRunner;
    }

    private ContinuousProcessingSchedulerRunner
    createSchedulerRunner(
        Connection connection,
        Clock clock,
        ProcessingSchedulerWaitStrategy waitStrategy,
        Settings settings
    ) {

        /*
         * ---------------------------------------------------------
         * SCHEDULER PERSISTENCE
         * ---------------------------------------------------------
         *
         * Todos os adapters desta unidade compartilham somente a
         * Connection exclusiva do scheduler.
         */
        JdbcProcessingScheduleAdapter scheduleAdapter =
            new JdbcProcessingScheduleAdapter(
                connection
            );

        JdbcProcessingRunRepositoryAdapter runRepository =
            new JdbcProcessingRunRepositoryAdapter(
                connection
            );

        JdbcProcessingJobQueueAdapter jobQueue =
            new JdbcProcessingJobQueueAdapter(
                connection
            );

        JdbcTransactionAdapter transactionAdapter =
            new JdbcTransactionAdapter(
                connection
            );

        /*
         * ---------------------------------------------------------
         * SCHEDULER APPLICATION USE CASE
         * ---------------------------------------------------------
         *
         * Este caso de uso somente:
         *
         * - adquire a janela;
         * - cria ProcessingRun;
         * - cria COLLECT_DEALS;
         * - confirma a janela.
         *
         * Nenhuma chamada externa à Amazon ocorre aqui.
         */
        ScheduleProcessingRunUseCase scheduleProcessingRunUseCase =
            new ScheduleProcessingRunUseCase(
                scheduleAdapter,
                runRepository,
                jobQueue,
                transactionAdapter,
                clock,
                settings.schedulerLeaseDuration(),
                settings.collectionMaxAttempts()
            );

        ScheduledProcessingTrigger trigger =
            scheduleProcessingRunUseCase::execute;

        return new ContinuousProcessingSchedulerRunner(
            trigger,
            settings.scheduleKey(),
            settings.schedulerInstanceId(),
            settings.schedulerPollInterval(),
            waitStrategy
        );
    }

    private ContinuousProcessingWorkerRunner
    createWorkerRunner(
        Connection connection,
        CollectionCollector collectionCollector,
        DealsParser dealsParser,
        ProductEnrichmentClient enrichmentClient,
        StructuredOperationalLogPort operationalLog,
        Clock clock,
        WorkerIdleWaitStrategy idleWaitStrategy,
        Settings settings
    ) {

        /*
         * ---------------------------------------------------------
         * ORCHESTRATION PERSISTENCE
         * ---------------------------------------------------------
         *
         * Toda a execução de um worker utiliza sua própria Connection.
         */
        JdbcProcessingRunRepositoryAdapter runRepository =
            new JdbcProcessingRunRepositoryAdapter(
                connection
            );

        JdbcDealCandidateRepositoryAdapter
            dealCandidateRepository =
            new JdbcDealCandidateRepositoryAdapter(
                connection
            );

        JdbcProcessingJobQueueAdapter jobQueue =
            new JdbcProcessingJobQueueAdapter(
                connection
            );

        JdbcEnrichedOfferLookupAdapter enrichedOfferLookup =
            new JdbcEnrichedOfferLookupAdapter(
                connection
            );

        JdbcDealEvaluationLookupAdapter evaluationLookup =
            new JdbcDealEvaluationLookupAdapter(
                connection
            );

        JdbcOfferSnapshotEvaluationLoadAdapter
            snapshotEvaluationLoad =
            new JdbcOfferSnapshotEvaluationLoadAdapter(
                connection
            );

        JdbcOfferSnapshotEvaluationLockAdapter
            snapshotEvaluationLock =
            new JdbcOfferSnapshotEvaluationLockAdapter(
                connection
            );

        JdbcTransactionAdapter transactionAdapter =
            new JdbcTransactionAdapter(
                connection
            );

        /*
         * ---------------------------------------------------------
         * PRODUCT
         * ---------------------------------------------------------
         */
        ProductRepository productRepository =
            new ProductRepository(
                connection
            );

        ProductJdbcPersistenceAdapter productPersistence =
            new ProductJdbcPersistenceAdapter(
                productRepository
            );

        /*
         * ---------------------------------------------------------
         * OFFER SNAPSHOT
         * ---------------------------------------------------------
         */
        OfferSnapshotFactory offerSnapshotFactory =
            new OfferSnapshotFactory();

        OfferSnapshotRepository offerSnapshotRepository =
            new OfferSnapshotRepository(
                connection
            );

        OfferSnapshotJdbcPersistenceAdapter
            offerSnapshotPersistence =
            new OfferSnapshotJdbcPersistenceAdapter(
                offerSnapshotRepository
            );

        /*
         * ---------------------------------------------------------
         * PAYMENT CONDITIONS
         * ---------------------------------------------------------
         */
        OfferPaymentConditionRepository paymentRepository =
            new OfferPaymentConditionRepository(
                connection
            );

        PaymentConditionJdbcPersistenceAdapter
            paymentConditionPersistence =
            new PaymentConditionJdbcPersistenceAdapter(
                paymentRepository
            );

        /*
         * ---------------------------------------------------------
         * OFFER EVIDENCE
         * ---------------------------------------------------------
         */
        OfferEvidenceJdbcRepository evidenceRepository =
            new OfferEvidenceJdbcRepository(
                connection
            );

        OfferEvidenceJdbcPersistenceAdapter
            evidencePersistence =
            new OfferEvidenceJdbcPersistenceAdapter(
                evidenceRepository
            );

        /*
         * ---------------------------------------------------------
         * EVALUATION PROFILES
         * ---------------------------------------------------------
         */
        FilterProfileJdbcRepository filterProfileRepository =
            new FilterProfileJdbcRepository(
                connection
            );

        ScoreProfileJdbcRepository scoreProfileRepository =
            new ScoreProfileJdbcRepository(
                connection
            );

        /*
         * ---------------------------------------------------------
         * DOMAIN SERVICES
         * ---------------------------------------------------------
         */
        CommercialFilterEngine commercialFilterEngine =
            new CommercialFilterEngine();

        ScoreEngine scoreEngine =
            new ScoreEngine();

        BestCashDiscountSelector bestCashDiscountSelector =
            new BestCashDiscountSelector();

        /*
         * ---------------------------------------------------------
         * HISTORY / MOMENTUM
         * ---------------------------------------------------------
         */
        OfferHistoryJdbcRepository offerHistoryRepository =
            new OfferHistoryJdbcRepository(
                connection
            );

        SnapshotEvolutionCalculator snapshotEvolutionCalculator =
            new SnapshotEvolutionCalculator(
                bestCashDiscountSelector
            );

        MomentumEngine momentumEngine =
            new MomentumEngine();

        MomentumCalculationService momentumCalculationService =
            new MomentumCalculationService(
                offerHistoryRepository,
                snapshotEvolutionCalculator,
                momentumEngine
            );

        /*
         * ---------------------------------------------------------
         * DEAL EVALUATION
         * ---------------------------------------------------------
         */
        DealEvaluationJdbcRepository evaluationRepository =
            new DealEvaluationJdbcRepository(
                connection
            );

        MomentumAuditJdbcRepository momentumAuditRepository =
            new MomentumAuditJdbcRepository(
                connection
            );

        AmazonDealEvaluationApplicationService evaluationService =
            new AmazonDealEvaluationApplicationService(
                new AmazonEligibilityValidator(),
                commercialFilterEngine,
                filterProfileRepository,
                scoreProfileRepository,
                scoreEngine,
                bestCashDiscountSelector,
                momentumCalculationService,
                evaluationRepository,
                momentumAuditRepository
            );

        /*
         * ---------------------------------------------------------
         * PHASE 12 USE CASES
         * ---------------------------------------------------------
         */
        CollectDealsUseCase collectDealsUseCase =
            new CollectDealsUseCase(
                runRepository,
                dealCandidateRepository,
                jobQueue,
                collectionCollector,
                dealsParser,
                transactionAdapter,
                clock,
                settings.enrichmentMaxAttempts()
            );

        EnrichDealUseCase enrichDealUseCase =
            new EnrichDealUseCase(
                dealCandidateRepository,
                enrichedOfferLookup,
                enrichmentClient,
                productPersistence,
                offerSnapshotFactory,
                offerSnapshotPersistence,
                paymentConditionPersistence,
                evidencePersistence,
                jobQueue,
                transactionAdapter,
                clock,
                settings.evaluationMaxAttempts()
            );

        EvaluateDealUseCase evaluateDealUseCase =
            new EvaluateDealUseCase(
                evaluationLookup,
                snapshotEvaluationLoad,
                snapshotEvaluationLock,
                evaluationService,
                transactionAdapter,
                clock
            );

        /*
         * ---------------------------------------------------------
         * JOB DISPATCH
         * ---------------------------------------------------------
         *
         * O dispatcher continua pertencendo à FASE 12.
         *
         * Nenhuma nova taxonomia de jobs é criada nesta fase.
         */
        DefaultProcessingJobExecutor jobExecutor =
            new DefaultProcessingJobExecutor(
                collectDealsUseCase::execute,
                enrichDealUseCase::execute,
                evaluateDealUseCase::execute
            );

        /*
         * ---------------------------------------------------------
         * FAILURE / RETRY
         * ---------------------------------------------------------
         *
         * Reutiliza exatamente o classificador e o backoff da
         * orquestração existente.
         *
         * A FASE 17 não cria um segundo motor de retry.
         */
        DefaultProcessingFailureClassifier failureClassifier =
            new DefaultProcessingFailureClassifier();

        ExponentialRetryBackoffPolicy retryBackoffPolicy =
            new ExponentialRetryBackoffPolicy(
                settings.retryBaseDelay(),
                settings.retryMaxDelay()
            );

        ProcessingJobFailureHandler failureHandler =
            new ProcessingJobFailureHandler(
                failureClassifier,
                retryBackoffPolicy,
                jobQueue
            );

        /*
         * ---------------------------------------------------------
         * WORKER
         * ---------------------------------------------------------
         */
        ProcessingWorker worker =
            new ProcessingWorker(
                settings.workerId(),
                jobQueue,
                jobExecutor,
                failureHandler,
                clock,
                operationalLog
            );

        return new ContinuousProcessingWorkerRunner(
            worker,
            settings.workerIdleDelay(),
            idleWaitStrategy
        );
    }

    private static String requireText(
        String value,
        String name
    ) {

        Objects.requireNonNull(
            value,
            name + " must not be null"
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                name + " must not be blank"
            );
        }

        return value;
    }

    private static Duration requirePositiveDuration(
        Duration value,
        String name
    ) {

        Objects.requireNonNull(
            value,
            name + " must not be null"
        );

        if (value.isZero()
            || value.isNegative()) {

            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }

        return value;
    }

    private static int requirePositiveInt(
        int value,
        String name
    ) {

        if (value <= 0) {
            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }

        return value;
    }

    /**
     * Configuração operacional necessária para montar scheduler e worker.
     *
     * <p>schedulerPollInterval não é a frequência da coleta.</p>
     *
     * <p>A frequência funcional da coleta continua persistida em
     * ProcessingSchedule e pode ser alterada pelos controles
     * operacionais implementados anteriormente.</p>
     */
    public record Settings(
        String scheduleKey,
        String schedulerInstanceId,
        String workerId,
        Duration schedulerPollInterval,
        Duration schedulerLeaseDuration,
        Duration workerIdleDelay,
        int collectionMaxAttempts,
        int enrichmentMaxAttempts,
        int evaluationMaxAttempts,
        Duration retryBaseDelay,
        Duration retryMaxDelay
    ) {

        public Settings {

            scheduleKey =
                requireText(
                    scheduleKey,
                    "scheduleKey"
                );

            schedulerInstanceId =
                requireText(
                    schedulerInstanceId,
                    "schedulerInstanceId"
                );

            workerId =
                requireText(
                    workerId,
                    "workerId"
                );

            schedulerPollInterval =
                requirePositiveDuration(
                    schedulerPollInterval,
                    "schedulerPollInterval"
                );

            schedulerLeaseDuration =
                requirePositiveDuration(
                    schedulerLeaseDuration,
                    "schedulerLeaseDuration"
                );

            workerIdleDelay =
                requirePositiveDuration(
                    workerIdleDelay,
                    "workerIdleDelay"
                );

            collectionMaxAttempts =
                requirePositiveInt(
                    collectionMaxAttempts,
                    "collectionMaxAttempts"
                );

            enrichmentMaxAttempts =
                requirePositiveInt(
                    enrichmentMaxAttempts,
                    "enrichmentMaxAttempts"
                );

            evaluationMaxAttempts =
                requirePositiveInt(
                    evaluationMaxAttempts,
                    "evaluationMaxAttempts"
                );

            retryBaseDelay =
                requirePositiveDuration(
                    retryBaseDelay,
                    "retryBaseDelay"
                );

            retryMaxDelay =
                requirePositiveDuration(
                    retryMaxDelay,
                    "retryMaxDelay"
                );

            if (retryBaseDelay.compareTo(
                retryMaxDelay
            ) > 0) {

                throw new IllegalArgumentException(
                    "retryBaseDelay must not be greater than "
                        + "retryMaxDelay"
                );
            }
        }
    }
}
