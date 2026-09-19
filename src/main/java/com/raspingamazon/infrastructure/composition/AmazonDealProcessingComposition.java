package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.deal.AmazonDealProcessingService;
import com.raspingamazon.application.deal.OfferSnapshotFactory;
import com.raspingamazon.application.evaluation.AmazonDealEvaluationApplicationService;
import com.raspingamazon.domain.validation.AmazonEligibilityValidator;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageEnrichmentClient;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageParser;
import com.raspingamazon.infrastructure.amazon.parser.AmazonDealsParser;
import com.raspingamazon.infrastructure.collection.HttpCollectionCollector;
import com.raspingamazon.infrastructure.http.JavaHttpTransport;
import com.raspingamazon.infrastructure.persistence.DealEvaluationJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferEvidenceJdbcRepository;
import com.raspingamazon.infrastructure.persistence.OfferPaymentConditionRepository;
import com.raspingamazon.infrastructure.persistence.OfferSnapshotRepository;
import com.raspingamazon.infrastructure.persistence.ProductRepository;
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
 * <p>Esta classe é responsável exclusivamente por montar o grafo de
 * dependências concreto da aplicação.</p>
 *
 * <p>Ela não contém regras de negócio e não executa o processamento.
 * O comportamento continua pertencendo a AmazonDealProcessingService.</p>
 *
 * <p>A Connection é recebida externamente de propósito. Isso prepara
 * o projeto para que a FASE 8.5-F defina a unidade transacional sem
 * esconder commit/rollback dentro dos repositories.</p>
 */
public final class AmazonDealProcessingComposition {

    private static final Duration HTTP_TIMEOUT =
            Duration.ofSeconds(
                    20
            );

    private AmazonDealProcessingComposition() {
    }

    /**
     * Monta o serviço com dependências padrão de produção.
     *
     * @param connection conexão JDBC compartilhada pelos repositories
     * @return serviço pronto para processar uma CollectionRequest
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
     * Variante injetável usada para testes e futura composição
     * transacional.
     *
     * <p>Clock e HttpClient entram explicitamente para evitar que a
     * construção do grafo fique presa a singletons ou estado global.</p>
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
         * Coleta da página de ofertas.
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
         * Interpretação do conteúdo da página de ofertas.
         */
        AmazonDealsParser dealsParser =
                new AmazonDealsParser();

        /*
         * Enriquecimento pela página individual do produto.
         */
        AmazonProductPageEnrichmentClient enrichmentClient =
                new AmazonProductPageEnrichmentClient(
                        httpClient,
                        new AmazonProductPageParser()
                );

        /*
         * Persistência de Product.
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
         * Construção e persistência do OfferSnapshot.
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
         * Condições comerciais.
         *
         * Atualmente o OfferSnapshotFactory pode produzir lista vazia.
         * O ponto de persistência já está corretamente reservado para
         * quando as condições comerciais forem transportadas pelo fluxo.
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
         * Evidências auditáveis de seller e delivery.
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
         * Avaliação estrutural Amazon.
         */
        DealEvaluationJdbcRepository evaluationRepository =
                new DealEvaluationJdbcRepository(
                        connection
                );

        AmazonDealEvaluationApplicationService
                evaluationService =
                new AmazonDealEvaluationApplicationService(
                        new AmazonEligibilityValidator(),
                        evaluationRepository
                );

        /*
         * Serviço vertical final.
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
                clock
        );
    }

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