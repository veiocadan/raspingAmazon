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
 * Orquestra o fluxo vertical síncrono de processamento das ofertas.
 *
 * <p>O pipeline possui duas regiões distintas:</p>
 *
 * <ol>
 *     <li>operações externas e sem persistência;</li>
 *     <li>operações persistentes executadas dentro de transação.</li>
 * </ol>
 *
 * <p>Fluxo:</p>
 *
 * <pre>
 * collect
 * parse
 *
 * para cada oferta:
 *     enrich
 *
 *     BEGIN
 *         upsert Product
 *         insert/reuse OfferSnapshot
 *
 *         se snapshot foi criado:
 *             persist PaymentConditions
 *             persist Evidence
 *             persist DealEvaluation
 *
 *     COMMIT
 * </pre>
 *
 * <p>A identidade idempotente do snapshot permite que a mesma
 * observação seja reprocessada sem duplicar histórico nem avaliação.</p>
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
     * Coleta a origem e processa sequencialmente as ofertas encontradas.
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
             * O enrichment executa I/O externo e permanece fora
             * da transação JDBC.
             */
            ProductEnrichmentResult enrichmentResult =
                    enrichmentClient.enrich(
                            parsedDeal
                    );

            ProcessedDealResult result =
                    transactionPort.execute(
                            () -> persistDeal(
                                    parsedDeal,
                                    enrichmentResult
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
     * Persiste uma oferta completamente enriquecida.
     *
     * <p>Este método deve ser executado dentro da TransactionPort.</p>
     */
    private ProcessedDealResult persistDeal(
            ParsedDeal parsedDeal,
            ProductEnrichmentResult enrichmentResult
    ) {
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

        /*
         * A persistência agora informa explicitamente se esta
         * observação foi criada ou se já existia.
         */
        PersistedOfferSnapshot persisted =
                offerSnapshotPersistencePort.save(
                        transientSnapshot
                );

        OfferSnapshot persistedSnapshot =
                persisted.snapshot();

        Long snapshotId =
                persistedSnapshot.id();

        if (snapshotId == null) {
            throw new IllegalStateException(
                    "Persisted OfferSnapshot must have an id"
            );
        }

        /*
         * Se a mesma observação já foi processada anteriormente,
         * nenhuma informação dependente deve ser duplicada.
         *
         * Como snapshot + dependências pertencem à mesma transação,
         * a existência do snapshot implica que a execução anterior
         * terminou com commit.
         */
        if (persisted.created()) {

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
        }

        return new ProcessedDealResult(
                parsedDeal,
                enrichmentResult,
                product,
                persistedSnapshot
        );
    }
}