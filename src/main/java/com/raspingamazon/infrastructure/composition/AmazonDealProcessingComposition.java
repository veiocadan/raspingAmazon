package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.deal.AmazonDealProcessingService;
import com.raspingamazon.application.deal.OfferSnapshotFactory;
import com.raspingamazon.application.evaluation.AmazonDealEvaluationApplicationService;
import com.raspingamazon.domain.filter.BestCashDiscountSelector;
import com.raspingamazon.domain.filter.CommercialFilterEngine;
import com.raspingamazon.domain.scoring.ScoreEngine;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageEnrichmentClient;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageParser;
import com.raspingamazon.infrastructure.amazon.parser.AmazonDealsParser;
import com.raspingamazon.infrastructure.collection.HttpCollectionCollector;
import com.raspingamazon.infrastructure.http.JavaHttpTransport;
import com.raspingamazon.infrastructure.persistence.DealEvaluationJdbcRepository;
import com.raspingamazon.infrastructure.persistence.FilterProfileJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferEvidenceJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferPaymentConditionRepository;
import com.raspingamazon.infrastructure.persistence.OfferSnapshotRepository;
import com.raspingamazon.infrastructure.persistence.ProductRepository;
import com.raspingamazon.infrastructure.persistence.ScoreProfileJdbcRepository;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcTransactionAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.OfferEvidenceJdbcPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.OfferSnapshotJdbcPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.PaymentConditionJdbcPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.ProductJdbcPersistenceAdapter;

import java.net.http.HttpClient;
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
 * e pelo JdbcTransactionAdapter. Essa característica é fundamental:
 * somente assim Product, OfferSnapshot, PaymentConditions, Evidence,
 * FilterProfile, ScoreProfile e DealEvaluation podem participar de uma
 * composição consistente.</p>
 *
 * <p>A classe não possui regras de negócio e não executa o fluxo.
 * Ela apenas conecta implementações concretas aos contratos da
 * aplicação.</p>
 */
public final class AmazonDealProcessingComposition {

    private static final Duration HTTP_TIMEOUT =
        Duration.ofSeconds(
            20
        );

    private AmazonDealProcessingComposition() {
    }

    /**
     * Monta o serviço utilizando dependências padrão de produção.
     *
     * @param connection conexão JDBC que será compartilhada por toda
     *                   a unidade de trabalho
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
     * Variante injetável da composição.
     *
     * <p>Clock e HttpClient são argumentos para manter o composition
     * root testável e evitar estado global.</p>
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
         * COLETA
         * ---------------------------------------------------------
         */
        JavaHttpTransport httpTransport =
            new JavaHttpTransport(
                httpClient,
                HTTP_TIMEOUT
            );

        HttpCollectionCollector collectionCollector =
            new HttpCollectionCollector(
                httpTransport,
                clock
            );

        /*
         * ---------------------------------------------------------
         * PARSER
         * ---------------------------------------------------------
         */
        AmazonDealsParser dealsParser =
            new AmazonDealsParser();

        /*
         * ---------------------------------------------------------
         * ENRICHMENT
         * ---------------------------------------------------------
         */
        AmazonProductPageEnrichmentClient enrichmentClient =
            new AmazonProductPageEnrichmentClient(
                httpClient,
                new AmazonProductPageParser()
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
         *
         * O perfil de score ativo também é lido da configuração
         * persistida e versionada.
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
         *
         * O motor permanece puro e independente de infraestrutura.
         */
        ScoreEngine scoreEngine =
            new ScoreEngine();

        /*
         * ---------------------------------------------------------
         * CASH DISCOUNT SELECTOR
         * ---------------------------------------------------------
         *
         * O mesmo conceito de melhor desconto à vista reconhecido
         * pelos filtros comerciais é reutilizado na construção do
         * ScoreInput.
         */
        BestCashDiscountSelector bestCashDiscountSelector =
            new BestCashDiscountSelector();

        /*
         * ---------------------------------------------------------
         * DEAL EVALUATION
         * ---------------------------------------------------------
         */
        DealEvaluationJdbcRepository evaluationRepository =
            new DealEvaluationJdbcRepository(
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
                evaluationRepository
            );

        /*
         * ---------------------------------------------------------
         * TRANSACTION BOUNDARY
         * ---------------------------------------------------------
         *
         * Todos os repositories acima e o transaction adapter usam
         * exatamente a mesma Connection.
         *
         * Assim:
         *
         * Product
         * Snapshot
         * PaymentConditions
         * Evidence
         * DealEvaluation
         *
         * pertencem à mesma unidade atômica de persistência.
         *
         * FilterProfile e ScoreProfile são somente lidos durante
         * a avaliação.
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
     * Cria o HttpClient compartilhado pelas chamadas HTTP do fluxo.
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
