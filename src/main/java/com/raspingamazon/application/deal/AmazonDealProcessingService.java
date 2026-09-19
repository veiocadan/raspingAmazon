package com.raspingamazon.application.deal;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.application.deal.port.OfferEvidencePersistencePort;
import com.raspingamazon.application.deal.port.OfferSnapshotPersistencePort;
import com.raspingamazon.application.deal.port.PaymentConditionPersistencePort;
import com.raspingamazon.application.deal.port.ProductPersistencePort;
import com.raspingamazon.application.deal.port.TransactionPort;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Product;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orquestra o fluxo vertical síncrono de processamento de ofertas.
 *
 * <p>A coleta e o parsing acontecem antes da fronteira transacional,
 * pois não produzem estado persistente.</p>
 *
 * <p>Cada oferta identificada é então processada dentro de uma unidade
 * transacional própria:</p>
 *
 * <ol>
 *     <li>enrich;</li>
 *     <li>upsert Product;</li>
 *     <li>persistir OfferSnapshot;</li>
 *     <li>persistir condições comerciais;</li>
 *     <li>persistir evidências;</li>
 *     <li>avaliar e persistir DealEvaluation;</li>
 *     <li>commit.</li>
 * </ol>
 *
 * <p>Qualquer falha após o início dessa unidade deve provocar rollback
 * integral através de TransactionPort.</p>
 */
public final class AmazonDealProcessingService {

    private final CollectionCollector collectionCollector;
    private final DealsParser dealsParser;
    private final ProductEnrichmentClient enrichmentClient;

    private final ProductPersistencePort productPersistencePort;

    private final OfferSnapshotFactory offerSnapshotFactory;

    private final OfferSnapshotPersistencePort
            offerSnapshotPersistencePort;

    private final PaymentConditionPersistencePort
            paymentConditionPersistencePort;

    private final OfferEvidencePersistencePort
            offerEvidencePersistencePort;

    private final DealEvaluationProcessingPort
            dealEvaluationProcessingPort;

    private final TransactionPort transactionPort;

    private final Clock clock;

    public AmazonDealProcessingService(
            CollectionCollector collectionCollector,
            DealsParser dealsParser,
            ProductEnrichmentClient enrichmentClient,
            ProductPersistencePort productPersistencePort,
            OfferSnapshotFactory offerSnapshotFactory,
            OfferSnapshotPersistencePort offerSnapshotPersistencePort,
            PaymentConditionPersistencePort paymentConditionPersistencePort,
            OfferEvidencePersistencePort offerEvidencePersistencePort,
            DealEvaluationProcessingPort dealEvaluationProcessingPort,
            TransactionPort transactionPort,
            Clock clock
    ) {
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

        this.dealEvaluationProcessingPort =
                Objects.requireNonNull(
                        dealEvaluationProcessingPort,
                        "dealEvaluationProcessingPort must not be null"
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
    }

    /**
     * Executa a coleta e processa sequencialmente todas as ofertas.
     */
    public List<ProcessedDealResult> process(
            CollectionRequest request
    ) {
        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        CollectionResult collectionResult =
                collectionCollector.collect(
                        request
                );

        List<ParsedDeal> parsedDeals =
                dealsParser.parse(
                        collectionResult
                );

        List<ProcessedDealResult> results =
                new ArrayList<>();

        for (ParsedDeal parsedDeal : parsedDeals) {

            /*
             * Cada oferta representa uma unidade atômica.
             *
             * Se qualquer etapa persistente falhar, nenhuma gravação
             * parcial daquela oferta deve permanecer.
             */
            ProcessedDealResult result =
                    transactionPort.execute(
                            () -> processDeal(
                                    parsedDeal
                            )
                    );

            results.add(
                    result
            );
        }

        return List.copyOf(
                results
        );
    }

    /**
     * Processa uma única oferta.
     *
     * <p>Este método deve ser chamado dentro de TransactionPort.</p>
     */
    private ProcessedDealResult processDeal(
            ParsedDeal parsedDeal
    ) {
        ProductEnrichmentResult enrichmentResult =
                enrichmentClient.enrich(
                        parsedDeal
                );

        Product product =
                productPersistencePort.upsert(
                        parsedDeal
                );

        OfferSnapshot transientSnapshot =
                offerSnapshotFactory.create(
                        product,
                        parsedDeal,
                        enrichmentResult
                );

        OfferSnapshot persistedSnapshot =
                offerSnapshotPersistencePort.save(
                        transientSnapshot
                );

        Long snapshotId =
                persistedSnapshot.id();

        if (snapshotId == null) {
            throw new IllegalStateException(
                    "Persisted OfferSnapshot must have an id"
            );
        }

        paymentConditionPersistencePort.saveAll(
                snapshotId,
                persistedSnapshot.paymentConditions()
        );

        offerEvidencePersistencePort.save(
                snapshotId,
                enrichmentResult
        );

        dealEvaluationProcessingPort.evaluateAndPersist(
                persistedSnapshot,
                OffsetDateTime.now(
                        clock
                )
        );

        return new ProcessedDealResult(
                parsedDeal,
                enrichmentResult,
                product,
                persistedSnapshot
        );
    }
}