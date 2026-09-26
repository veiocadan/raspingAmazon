package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.collection.contract.CollectionCollector;
import com.raspingamazon.application.deal.AmazonDealProcessingService;
import com.raspingamazon.application.deal.OfferSnapshotFactory;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.evaluation.AmazonDealEvaluationApplicationService;
import com.raspingamazon.application.momentum.MomentumCalculationService;
import com.raspingamazon.application.observability.BestEffortIntegrationObservationRecorder;
import com.raspingamazon.application.observability.port.IntegrationObservationPersistencePort;
import com.raspingamazon.application.observability.port.IntegrationObservationRecorder;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;
import com.raspingamazon.application.orchestration.failure.DefaultProcessingFailureClassifier;
import com.raspingamazon.application.orchestration.failure.ProcessingFailureClassifier;
import com.raspingamazon.application.parsing.contract.DealsParser;
import com.raspingamazon.domain.filter.BestCashDiscountSelector;
import com.raspingamazon.domain.filter.CommercialFilterEngine;
import com.raspingamazon.domain.history.SnapshotEvolutionCalculator;
import com.raspingamazon.domain.momentum.MomentumEngine;
import com.raspingamazon.domain.scoring.ScoreEngine;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonCustomerReviewParser;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonPaymentConditionParser;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageEnrichmentClient;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageParser;
import com.raspingamazon.infrastructure.amazon.enrichment.HttpProductPageContentProvider;
import com.raspingamazon.infrastructure.amazon.enrichment.ProductPageContentProvider;
import com.raspingamazon.infrastructure.amazon.parser.AmazonDealsParser;
import com.raspingamazon.infrastructure.collection.HttpCollectionCollector;
import com.raspingamazon.infrastructure.http.JavaHttpTransport;
import com.raspingamazon.infrastructure.observability.JsonStructuredOperationalLogAdapter;
import com.raspingamazon.infrastructure.persistence.DealEvaluationJdbcRepository;
import com.raspingamazon.infrastructure.persistence.FilterProfileJdbcRepository;
import com.raspingamazon.infrastructure.persistence.MomentumAuditJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferEvidenceJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferHistoryJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferPaymentConditionRepository;
import com.raspingamazon.infrastructure.persistence.OfferSnapshotRepository;
import com.raspingamazon.infrastructure.persistence.ProductRepository;
import com.raspingamazon.infrastructure.persistence.ScoreProfileJdbcRepository;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcIntegrationObservationPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcTransactionAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.OfferEvidenceJdbcPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.OfferSnapshotJdbcPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.PaymentConditionJdbcPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.ProductJdbcPersistenceAdapter;

import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Composition root do fluxo síncrono de processamento de ofertas Amazon.
 *
 * <p>Esta classe monta o grafo concreto de dependências utilizado pela
 * aplicação.</p>
 *
 * <p>A mesma Connection JDBC é compartilhada por todos os repositories
 * e pelo JdbcTransactionAdapter. Dessa forma Product, OfferSnapshot,
 * PaymentConditions, Evidence, avaliação, histórico e auditorias
 * participam da mesma unidade de trabalho.</p>
 *
 * <p>O composition root possui duas formas de montagem:</p>
 *
 * <ol>
 *     <li>
 *         composição padrão de produção, que cria coleta, parser,
 *         enrichment HTTP e observabilidade das integrações;
 *     </li>
 *     <li>
 *         composição com fronteiras externas injetadas, útil quando
 *         outro mecanismo de aquisição precisa reutilizar exatamente
 *         o mesmo pipeline persistente e de decisão.
 *     </li>
 * </ol>
 *
 * <p>A variante injetável não altera regras de negócio e não cria
 * um pipeline alternativo. Ela somente permite substituir as
 * fronteiras anteriores à persistência.</p>
 */
public final class AmazonDealProcessingComposition {

    private static final Duration HTTP_TIMEOUT =
        Duration.ofSeconds(
            20
        );

    private static final String COLLECTION_INTEGRATION =
        "amazon-deals-http";

    private static final String PRODUCT_PAGE_INTEGRATION =
        "amazon-product-page";

    private AmazonDealProcessingComposition() {
    }

    /**
     * Monta o serviço utilizando dependências padrão de produção.
     *
     * @param connection conexão JDBC compartilhada pela unidade
     *                   de trabalho
     * @return serviço vertical pronto para execução
     */
    public static AmazonDealProcessingService create(
        Connection connection
    ) {

        return create(
            connection,
            Clock.systemUTC(),
            createHttpClient()
        );
    }

    /**
     * Variante padrão com Clock e HttpClient injetáveis.
     *
     * <p>Esta é a composição HTTP convencional da aplicação. Ela cria
     * as duas fronteiras externas observadas:</p>
     *
     * <ul>
     *     <li>coleta de deals;</li>
     *     <li>aquisição da página individual de produto.</li>
     * </ul>
     *
     * <p>As duas usam o mesmo recorder best-effort e o mesmo
     * ProcessingFailureClassifier, evitando fontes concorrentes de
     * classificação operacional.</p>
     */
    public static AmazonDealProcessingService create(
        Connection connection,
        Clock clock,
        HttpClient httpClient
    ) {

        Objects.requireNonNull(
            connection,
            "connection must not be null"
        );

        Objects.requireNonNull(
            clock,
            "clock must not be null"
        );

        Objects.requireNonNull(
            httpClient,
            "httpClient must not be null"
        );

        /*
         * ---------------------------------------------------------
         * INTEGRATION OBSERVABILITY
         * ---------------------------------------------------------
         *
         * O mesmo classificador utilizado pelo processamento fornece
         * TRANSIENT/PERMANENT e errorCode para todas as observações.
         *
         * O adapter JDBC utiliza a mesma Connection do pipeline e
         * protege transações externas com savepoints.
         *
         * O recorder best-effort garante que uma falha da própria
         * observabilidade não modifique o resultado funcional.
         */
        ProcessingFailureClassifier failureClassifier =
            new DefaultProcessingFailureClassifier();

        IntegrationObservationPersistencePort
            observationPersistencePort =
            new JdbcIntegrationObservationPersistenceAdapter(
                connection
            );

        /*
         * Falhas da própria observabilidade são diagnósticas e são
         * escritas em JSON Lines no stderr.
         *
         * O writer não é fechado porque System.err pertence ao
         * processo.
         */
        StructuredOperationalLogPort operationalLog =
            new JsonStructuredOperationalLogAdapter(
                new PrintWriter(
                    System.err,
                    true,
                    StandardCharsets.UTF_8
                ),
                clock
            );

        IntegrationObservationRecorder observationRecorder =
            new BestEffortIntegrationObservationRecorder(
                observationPersistencePort,
                failureClassifier,
                operationalLog
            );

        /*
         * ---------------------------------------------------------
         * COLLECTION HTTP
         * ---------------------------------------------------------
         */
        JavaHttpTransport httpTransport =
            new JavaHttpTransport(
                httpClient,
                HTTP_TIMEOUT
            );

        CollectionCollector collectionCollector =
            new HttpCollectionCollector(
                httpTransport,
                clock,
                COLLECTION_INTEGRATION,
                observationRecorder,
                failureClassifier
            );

        /*
         * ---------------------------------------------------------
         * DEALS PARSER
         * ---------------------------------------------------------
         */
        DealsParser dealsParser =
            new AmazonDealsParser();

        /*
         * ---------------------------------------------------------
         * PRODUCT PAGE ACQUISITION
         * ---------------------------------------------------------
         *
         * O provider permanece separado do client de enrichment.
         * Portanto, no futuro, HTTP bruto pode ser substituído por
         * DOM renderizado sem alterar a instrumentação do client.
         *
         * O Clock compartilhado também é fornecido ao provider para
         * que collectedAt e observedAt usem a mesma referência civil.
         */
        ProductPageContentProvider productPageContentProvider =
            new HttpProductPageContentProvider(
                httpClient,
                clock
            );

        /*
         * ---------------------------------------------------------
         * PRODUCT ENRICHMENT
         * ---------------------------------------------------------
         *
         * A observação envolve somente
         * ProductPageContentProvider.load().
         *
         * Parsing de seller, delivery, reviews e condições comerciais
         * fica deliberadamente fora da latência da integração.
         */
        ProductEnrichmentClient enrichmentClient =
            new AmazonProductPageEnrichmentClient(
                productPageContentProvider,
                new AmazonProductPageParser(),
                new AmazonPaymentConditionParser(),
                new AmazonCustomerReviewParser(),
                clock,
                PRODUCT_PAGE_INTEGRATION,
                observationRecorder,
                failureClassifier
            );

        return create(
            connection,
            clock,
            collectionCollector,
            dealsParser,
            enrichmentClient
        );
    }

    /**
     * Monta o mesmo pipeline persistente e de decisão utilizando
     * fronteiras externas fornecidas pelo chamador.
     *
     * <p>Este método existe para que mecanismos alternativos de
     * aquisição possam reutilizar o pipeline verdadeiro sem copiar
     * a composição de repositories, filtros, score, histórico,
     * momentum e avaliação.</p>
     *
     * <p>Exemplos de fronteiras substituíveis:</p>
     *
     * <ul>
     *     <li>coleta previamente realizada;</li>
     *     <li>parser limitado a um candidato diagnóstico;</li>
     *     <li>enrichment baseado em DOM renderizado.</li>
     * </ul>
     *
     * <p>A partir de ProductPersistencePort, a composição é
     * exatamente a mesma usada pelo fluxo padrão.</p>
     *
     * <p>Esta variante permanece deliberadamente neutra em relação
     * à observabilidade das fronteiras externas. O chamador que
     * fornece CollectionCollector ou ProductEnrichmentClient
     * alternativos também controla a composição dessas fronteiras.</p>
     *
     * @param connection conexão JDBC compartilhada
     * @param clock relógio da execução
     * @param collectionCollector coletor de origem
     * @param dealsParser parser das ofertas
     * @param enrichmentClient enrichment da página individual
     * @return serviço vertical pronto para execução
     */
    public static AmazonDealProcessingService create(
        Connection connection,
        Clock clock,
        CollectionCollector collectionCollector,
        DealsParser dealsParser,
        ProductEnrichmentClient enrichmentClient
    ) {

        Objects.requireNonNull(
            connection,
            "connection must not be null"
        );

        Objects.requireNonNull(
            clock,
            "clock must not be null"
        );

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

        /*
         * ---------------------------------------------------------
         * PRODUCT
         * ---------------------------------------------------------
         */
        ProductRepository productRepository =
            new ProductRepository(
                connection
            );

        ProductJdbcPersistenceAdapter productPersistenceAdapter =
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
            offerSnapshotPersistenceAdapter =
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
            paymentConditionPersistenceAdapter =
            new PaymentConditionJdbcPersistenceAdapter(
                paymentRepository
            );

        /*
         * ---------------------------------------------------------
         * EVIDENCE
         * ---------------------------------------------------------
         */
        OfferEvidenceJdbcRepository evidenceRepository =
            new OfferEvidenceJdbcRepository(
                connection
            );

        OfferEvidenceJdbcPersistenceAdapter
            evidencePersistenceAdapter =
            new OfferEvidenceJdbcPersistenceAdapter(
                evidenceRepository
            );

        /*
         * ---------------------------------------------------------
         * FILTER PROFILE
         * ---------------------------------------------------------
         */
        FilterProfileJdbcRepository filterProfileRepository =
            new FilterProfileJdbcRepository(
                connection
            );

        /*
         * ---------------------------------------------------------
         * SCORE PROFILE
         * ---------------------------------------------------------
         */
        ScoreProfileJdbcRepository scoreProfileRepository =
            new ScoreProfileJdbcRepository(
                connection
            );

        /*
         * ---------------------------------------------------------
         * COMMERCIAL FILTER ENGINE
         * ---------------------------------------------------------
         */
        CommercialFilterEngine commercialFilterEngine =
            new CommercialFilterEngine();

        /*
         * ---------------------------------------------------------
         * SCORE ENGINE
         * ---------------------------------------------------------
         */
        ScoreEngine scoreEngine =
            new ScoreEngine();

        /*
         * ---------------------------------------------------------
         * CASH DISCOUNT SELECTOR
         * ---------------------------------------------------------
         */
        BestCashDiscountSelector bestCashDiscountSelector =
            new BestCashDiscountSelector();

        /*
         * ---------------------------------------------------------
         * HISTÓRICO
         * ---------------------------------------------------------
         */
        OfferHistoryJdbcRepository offerHistoryRepository =
            new OfferHistoryJdbcRepository(
                connection
            );

        /*
         * ---------------------------------------------------------
         * SNAPSHOT EVOLUTION
         * ---------------------------------------------------------
         */
        SnapshotEvolutionCalculator snapshotEvolutionCalculator =
            new SnapshotEvolutionCalculator(
                bestCashDiscountSelector
            );

        /*
         * ---------------------------------------------------------
         * MOMENTUM ENGINE
         * ---------------------------------------------------------
         */
        MomentumEngine momentumEngine =
            new MomentumEngine();

        /*
         * ---------------------------------------------------------
         * MOMENTUM CALCULATION SERVICE
         * ---------------------------------------------------------
         */
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

        /*
         * ---------------------------------------------------------
         * MOMENTUM AUDIT
         * ---------------------------------------------------------
         */
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
         * TRANSACTION BOUNDARY
         * ---------------------------------------------------------
         *
         * Todos os repositories e o transaction adapter utilizam
         * exatamente a mesma Connection.
         *
         * Quando o chamador já tiver aberto uma transação externa
         * com autoCommit=false, JdbcTransactionAdapter utilizará
         * savepoint e não fará commit da transação externa.
         */
        JdbcTransactionAdapter transactionAdapter =
            new JdbcTransactionAdapter(
                connection
            );

        /*
         * ---------------------------------------------------------
         * APPLICATION SERVICE
         * ---------------------------------------------------------
         */
        return new AmazonDealProcessingService(
            collectionCollector,
            dealsParser,
            enrichmentClient,
            productPersistenceAdapter,
            offerSnapshotFactory,
            offerSnapshotPersistenceAdapter,
            paymentConditionPersistenceAdapter,
            evidencePersistenceAdapter,
            evaluationService,
            transactionAdapter,
            clock
        );
    }

    /**
     * Cria o HttpClient compartilhado pelas chamadas HTTP do fluxo
     * padrão.
     */
    private static HttpClient createHttpClient() {

        return HttpClient.newBuilder()
            .connectTimeout(
                HTTP_TIMEOUT
            )
            .followRedirects(
                HttpClient.Redirect.NORMAL
            )
            .build();
    }
}
