package com.raspingamazon.application.orchestration.enrichment;

import com.raspingamazon.application.deal.OfferSnapshotFactory;
import com.raspingamazon.application.deal.PersistedOfferSnapshot;
import com.raspingamazon.application.deal.port.OfferEvidencePersistencePort;
import com.raspingamazon.application.deal.port.OfferSnapshotPersistencePort;
import com.raspingamazon.application.deal.port.PaymentConditionPersistencePort;
import com.raspingamazon.application.deal.port.ProductPersistencePort;
import com.raspingamazon.application.deal.port.TransactionPort;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.observability.OperationalLogContext;
import com.raspingamazon.application.orchestration.DealCandidate;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.port.DealCandidateRepositoryPort;
import com.raspingamazon.application.orchestration.port.EnrichedOfferLookupPort;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Product;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Caso de uso da etapa ENRICH_DEAL.
 *
 * <p>Responsabilidades:</p>
 *
 * <ol>
 *     <li>carregar o DealCandidate persistido;</li>
 *     <li>detectar enriquecimento previamente concluído;</li>
 *     <li>executar enriquecimento externo quando necessário;</li>
 *     <li>persistir Product, OfferSnapshot, condições e evidências;</li>
 *     <li>correlacionar DealCandidate e OfferSnapshot;</li>
 *     <li>criar o job EVALUATE_DEAL.</li>
 * </ol>
 *
 * <p>Esta etapa deliberadamente não executa avaliação.</p>
 */
public final class EnrichDealUseCase {

    private final DealCandidateRepositoryPort
        dealCandidateRepository;

    private final EnrichedOfferLookupPort
        enrichedOfferLookup;

    private final ProductEnrichmentClient
        enrichmentClient;

    private final ProductPersistencePort
        productPersistencePort;

    private final OfferSnapshotFactory
        offerSnapshotFactory;

    private final OfferSnapshotPersistencePort
        offerSnapshotPersistencePort;

    private final PaymentConditionPersistencePort
        paymentConditionPersistencePort;

    private final OfferEvidencePersistencePort
        offerEvidencePersistencePort;

    private final ProcessingJobQueuePort
        processingJobQueue;

    private final TransactionPort
        transactionPort;

    private final Clock
        clock;

    private final int
        evaluationMaxAttempts;

    public EnrichDealUseCase(
        DealCandidateRepositoryPort dealCandidateRepository,
        EnrichedOfferLookupPort enrichedOfferLookup,
        ProductEnrichmentClient enrichmentClient,
        ProductPersistencePort productPersistencePort,
        OfferSnapshotFactory offerSnapshotFactory,
        OfferSnapshotPersistencePort offerSnapshotPersistencePort,
        PaymentConditionPersistencePort paymentConditionPersistencePort,
        OfferEvidencePersistencePort offerEvidencePersistencePort,
        ProcessingJobQueuePort processingJobQueue,
        TransactionPort transactionPort,
        Clock clock,
        int evaluationMaxAttempts
    ) {

        this.dealCandidateRepository =
            Objects.requireNonNull(
                dealCandidateRepository,
                "dealCandidateRepository must not be null"
            );

        this.enrichedOfferLookup =
            Objects.requireNonNull(
                enrichedOfferLookup,
                "enrichedOfferLookup must not be null"
            );

        this.enrichmentClient =
            Objects.requireNonNull(
                enrichmentClient,
                "enrichmentClient must not be null"
            );

        this.productPersistencePort =
            Objects.requireNonNull(
                productPersistencePort,
                "productPersistencePort must not be null"
            );

        this.offerSnapshotFactory =
            Objects.requireNonNull(
                offerSnapshotFactory,
                "offerSnapshotFactory must not be null"
            );

        this.offerSnapshotPersistencePort =
            Objects.requireNonNull(
                offerSnapshotPersistencePort,
                "offerSnapshotPersistencePort must not be null"
            );

        this.paymentConditionPersistencePort =
            Objects.requireNonNull(
                paymentConditionPersistencePort,
                "paymentConditionPersistencePort must not be null"
            );

        this.offerEvidencePersistencePort =
            Objects.requireNonNull(
                offerEvidencePersistencePort,
                "offerEvidencePersistencePort must not be null"
            );

        this.processingJobQueue =
            Objects.requireNonNull(
                processingJobQueue,
                "processingJobQueue must not be null"
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

        if (evaluationMaxAttempts <= 0) {
            throw new IllegalArgumentException(
                "evaluationMaxAttempts must be positive"
            );
        }

        this.evaluationMaxAttempts =
            evaluationMaxAttempts;
    }

    /**
     * Enriquece um candidato persistido.
     *
     * @param dealCandidateId identidade do candidato
     * @return id do OfferSnapshot persistido
     */
    public long execute(
        long dealCandidateId
    ) {

        if (dealCandidateId <= 0) {
            throw new IllegalArgumentException(
                "dealCandidateId must be positive"
            );
        }

        DealCandidate candidate =
            dealCandidateRepository.findById(
                    dealCandidateId
                )
                .orElseThrow(
                    () -> new IllegalArgumentException(
                        "DealCandidate not found: "
                            + dealCandidateId
                    )
                );

        /*
         * Primeiro verificamos se uma execução anterior já chegou
         * a persistir o snapshot.
         *
         * Esse teste acontece antes da chamada externa.
         */
        OptionalLong existingSnapshotId =
            enrichedOfferLookup.findSnapshotId(
                candidate
            );

        if (existingSnapshotId.isPresent()) {

            long snapshotId =
                existingSnapshotId.getAsLong();

            /*
             * O vínculo candidato -> snapshot e o enqueue são
             * executados na mesma transação.
             *
             * O enqueue continua idempotente.
             *
             * Assim:
             *
             * - retries não duplicam EVALUATE_DEAL;
             * - snapshots produzidos anteriormente passam a possuir
             *   correlação explícita com o candidato;
             * - não existe commit do enqueue sem commit da correlação.
             */
            linkAndEnqueueEvaluation(
                dealCandidateId,
                snapshotId
            );

            return snapshotId;
        }

        ParsedDeal parsedDeal =
            candidate.parsedDeal();

        /*
         * I/O externo fora da transação JDBC.
         */
        ProductEnrichmentResult enrichmentResult =
            enrichmentClient.enrich(
                parsedDeal,
                enrichmentObservationContext(
                    candidate,
                    dealCandidateId,
                    parsedDeal
                )
            );

        OffsetDateTime persistedAt =
            OffsetDateTime.now(
                clock
            );

        return transactionPort.execute(
            () -> persistEnrichment(
                dealCandidateId,
                parsedDeal,
                enrichmentResult,
                persistedAt
            )
        );
    }

    /**
     * Correlação disponível antes da chamada externa de enrichment.
     *
     * <p>A run, o candidate, o tipo da etapa e o ASIN já são fatos
     * persistidos/conhecidos. snapshotId ainda não existe neste ponto
     * e jobId não pertence ao contrato deste caso de uso.</p>
     */
    private OperationalLogContext enrichmentObservationContext(
        DealCandidate candidate,
        long dealCandidateId,
        ParsedDeal parsedDeal
    ) {

        return new OperationalLogContext(
            candidate.processingRunId(),
            null,
            ProcessingJobType.ENRICH_DEAL,
            dealCandidateId,
            null,
            null,
            null,
            parsedDeal.asin(),
            null
        );
    }

    private long persistEnrichment(
        long dealCandidateId,
        ParsedDeal parsedDeal,
        ProductEnrichmentResult enrichmentResult,
        OffsetDateTime persistedAt
    ) {

        Product product =
            productPersistencePort.upsert(
                parsedDeal
            );

        OfferSnapshot transientSnapshot =
            offerSnapshotFactory.create(
                product,
                parsedDeal,
                enrichmentResult,
                enrichmentResult.paymentConditions()
            );

        PersistedOfferSnapshot persisted =
            offerSnapshotPersistencePort.save(
                transientSnapshot
            );

        OfferSnapshot snapshot =
            persisted.snapshot();

        Long snapshotId =
            snapshot.id();

        if (snapshotId == null) {
            throw new IllegalStateException(
                "Persisted OfferSnapshot must have an id"
            );
        }

        /*
         * Somente quem criou o snapshot deve criar suas dependências.
         *
         * Se save() reutilizou uma identidade já persistida, assumimos
         * que a transação que criou aquela observação também criou
         * payment conditions e evidence.
         */
        if (persisted.created()) {

            paymentConditionPersistencePort.saveAll(
                snapshotId,
                snapshot.paymentConditions()
            );

            offerEvidencePersistencePort.save(
                snapshotId,
                enrichmentResult
            );
        }

        /*
         * A correlação faz parte da mesma unidade de trabalho que
         * persiste o enrichment e cria EVALUATE_DEAL.
         *
         * Se qualquer operação posterior falhar, JdbcTransactionAdapter
         * desfaz também este vínculo.
         */
        dealCandidateRepository.linkOfferSnapshot(
            dealCandidateId,
            snapshotId
        );

        processingJobQueue.enqueue(
            ProcessingJobSubmission.evaluateDeal(
                snapshotId,
                evaluationIdempotencyKey(
                    snapshotId
                ),
                evaluationMaxAttempts,
                persistedAt
            )
        );

        return snapshotId;
    }

    private void linkAndEnqueueEvaluation(
        long dealCandidateId,
        long snapshotId
    ) {

        OffsetDateTime availableAt =
            OffsetDateTime.now(
                clock
            );

        transactionPort.execute(
            () -> {

                dealCandidateRepository.linkOfferSnapshot(
                    dealCandidateId,
                    snapshotId
                );

                processingJobQueue.enqueue(
                    ProcessingJobSubmission.evaluateDeal(
                        snapshotId,
                        evaluationIdempotencyKey(
                            snapshotId
                        ),
                        evaluationMaxAttempts,
                        availableAt
                    )
                );

                return snapshotId;
            }
        );
    }

    private String evaluationIdempotencyKey(
        long snapshotId
    ) {

        return "evaluate:"
            + snapshotId;
    }
}
