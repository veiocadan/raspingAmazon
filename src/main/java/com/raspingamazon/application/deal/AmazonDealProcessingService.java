package com.raspingamazon.application.deal;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.CollectionResult;
import com.raspingamazon.application.deal.port.DealEvaluationProcessingPort;
import com.raspingamazon.application.deal.port.OfferEvidencePersistencePort;
import com.raspingamazon.application.deal.port.OfferSnapshotPersistencePort;
import com.raspingamazon.application.deal.port.PaymentConditionPersistencePort;
import com.raspingamazon.application.deal.port.ProductPersistencePort;
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
 * <p>Ordem do pipeline:</p>
 *
 * <ol>
 *     <li>collect;</li>
 *     <li>parse;</li>
 *     <li>enrich;</li>
 *     <li>upsert Product;</li>
 *     <li>construir OfferSnapshot;</li>
 *     <li>persistir OfferSnapshot;</li>
 *     <li>persistir condições comerciais;</li>
 *     <li>persistir evidências;</li>
 *     <li>avaliar elegibilidade e persistir DealEvaluation.</li>
 * </ol>
 *
 * <p>Este serviço deliberadamente não controla transação JDBC.
 * A fronteira transacional será introduzida na FASE 8.5-F.</p>
 *
 * <p>Também não existem scheduler, fila, UI ou execução paralela
 * nesta fase. O objetivo é primeiro possuir um caminho síncrono,
 * determinístico e testável.</p>
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

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null"
                );
    }

    /**
     * Executa uma coleta completa e processa sequencialmente
     * todas as ofertas reconhecidas pelo parser.
     *
     * <p>Falhas não são silenciosamente ignoradas. Enquanto ainda
     * não existe a unidade transacional da FASE 8.5-F, qualquer
     * exceção interrompe o processamento e sobe para o chamador.</p>
     *
     * @param request origem da coleta
     * @return ofertas que completaram o pipeline
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

            ProcessedDealResult result =
                    processDeal(
                            parsedDeal
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
     * Processa uma única oferta já extraída pelo parser.
     */
    private ProcessedDealResult processDeal(
            ParsedDeal parsedDeal
    ) {
        ProductEnrichmentResult enrichmentResult =
                enrichmentClient.enrich(
                        parsedDeal
                );

        /*
         * Product representa identidade durável.
         * Portanto a operação é upsert e não insert cego.
         */
        Product product =
                productPersistencePort.upsert(
                        parsedDeal
                );

        /*
         * Nesta fase as condições comerciais disponíveis no
         * OfferSnapshotFactory continuam sendo transportadas
         * através do próprio snapshot.
         */
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

        /*
         * Mesmo quando a lista é vazia, chamamos a porta.
         *
         * Isso mantém o fluxo estruturalmente estável para quando
         * condições Pix/parcelamento estiverem presentes.
         */
        paymentConditionPersistencePort.saveAll(
                snapshotId,
                persistedSnapshot.paymentConditions()
        );

        /*
         * Seller/delivery e sua provenance são persistidos
         * separadamente do snapshot.
         */
        offerEvidencePersistencePort.save(
                snapshotId,
                enrichmentResult
        );

        /*
         * A avaliação acontece somente depois que o snapshot
         * e suas evidências possuem identidade persistente.
         */
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