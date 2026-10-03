package com.raspingamazon.infrastructure.bootstrap;

import com.raspingamazon.application.observability.BestEffortIntegrationObservationRecorder;
import com.raspingamazon.application.observability.port.IntegrationObservationRecorder;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;
import com.raspingamazon.application.orchestration.failure.DefaultProcessingFailureClassifier;
import com.raspingamazon.application.orchestration.failure.ProcessingFailureClassifier;
import com.raspingamazon.application.scheduling.EnsureProcessingScheduleUseCase;
import com.raspingamazon.infrastructure.amazon.AmazonDealsCollector;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonCustomerReviewParser;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonPaymentConditionParser;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageEnrichmentClient;
import com.raspingamazon.infrastructure.amazon.enrichment.AmazonProductPageParser;
import com.raspingamazon.infrastructure.amazon.enrichment.PlaywrightRenderedProductPageContentProvider;
import com.raspingamazon.infrastructure.amazon.parser.AmazonDealsParser;
import com.raspingamazon.infrastructure.collection.HttpCollectionCollector;
import com.raspingamazon.infrastructure.composition.ContinuousProcessingComposition;
import com.raspingamazon.infrastructure.config.ApplicationConfig;
import com.raspingamazon.infrastructure.config.ContinuousProcessingEnvironmentConfig;
import com.raspingamazon.infrastructure.config.ContinuousProcessingEnvironmentConfigProvider;
import com.raspingamazon.infrastructure.config.EnvironmentConfigProvider;
import com.raspingamazon.infrastructure.http.JavaHttpTransport;
import com.raspingamazon.infrastructure.observability.JsonStructuredOperationalLogAdapter;
import com.raspingamazon.infrastructure.persistence.DatabaseConnection;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcIntegrationObservationPersistenceAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingScheduleAdapter;
import com.raspingamazon.infrastructure.runtime.ContinuousProcessingRuntime;
import com.raspingamazon.infrastructure.runtime.ThreadSleepProcessingSchedulerWaitStrategy;
import com.raspingamazon.infrastructure.runtime.ThreadSleepWorkerIdleWaitStrategy;

import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Bootstrap do processo contínuo.
 *
 * <p>Responsabilidades:</p>
 *
 * <ol>
 *     <li>carregar configuração;</li>
 *     <li>abrir Connections independentes;</li>
 *     <li>garantir a existência do schedule;</li>
 *     <li>montar integrações Amazon;</li>
 *     <li>montar observabilidade;</li>
 *     <li>montar composition e runtime;</li>
 *     <li>transferir ownership dos recursos físicos para
 *         ContinuousProcessingApplication.</li>
 * </ol>
 */
public final class ContinuousProcessingBootstrap {

    private static final Duration COLLECTION_REQUEST_TIMEOUT =
        Duration.ofSeconds(
            30
        );

    private static final String COLLECTION_INTEGRATION =
        "amazon-deals";

    private static final String ENRICHMENT_INTEGRATION =
        "amazon-product-page";

    private ContinuousProcessingBootstrap() {
    }

    public static ContinuousProcessingApplication open() {

        ApplicationConfig databaseConfig =
            EnvironmentConfigProvider.load();

        ContinuousProcessingEnvironmentConfig runtimeConfig =
            ContinuousProcessingEnvironmentConfigProvider.load();

        Clock clock =
            Clock.systemUTC();

        PrintWriter operationalLogWriter =
            new PrintWriter(
                System.out,
                true
            );

        Connection schedulerConnection =
            null;

        Connection workerConnection =
            null;

        PlaywrightRenderedProductPageContentProvider
            productPageContentProvider =
            null;

        try {

            /*
             * Duas conexões físicas independentes.
             *
             * Elas poderão ser utilizadas simultaneamente pelas threads
             * do scheduler e worker.
             */
            schedulerConnection =
                DatabaseConnection.open(
                    databaseConfig
                );

            workerConnection =
                DatabaseConnection.open(
                    databaseConfig
                );

            /*
             * -----------------------------------------------------
             * INITIAL SCHEDULE
             * -----------------------------------------------------
             *
             * saveIfAbsent preserva qualquer estado operacional já
             * existente no PostgreSQL.
             */
            JdbcProcessingScheduleAdapter scheduleAdapter =
                new JdbcProcessingScheduleAdapter(
                    schedulerConnection
                );

            EnsureProcessingScheduleUseCase
                ensureProcessingScheduleUseCase =
                new EnsureProcessingScheduleUseCase(
                    scheduleAdapter,
                    clock
                );

            ensureProcessingScheduleUseCase.execute(
                runtimeConfig.scheduleKey(),
                runtimeConfig.source(),
                runtimeConfig.initialScheduleInterval()
            );

            /*
             * -----------------------------------------------------
             * OBSERVABILITY
             * -----------------------------------------------------
             */
            StructuredOperationalLogPort operationalLog =
                new JsonStructuredOperationalLogAdapter(
                    operationalLogWriter,
                    clock
                );

            ProcessingFailureClassifier failureClassifier =
                new DefaultProcessingFailureClassifier();

            JdbcIntegrationObservationPersistenceAdapter
                observationPersistence =
                new JdbcIntegrationObservationPersistenceAdapter(
                    workerConnection
                );

            IntegrationObservationRecorder observationRecorder =
                new BestEffortIntegrationObservationRecorder(
                    observationPersistence,
                    failureClassifier,
                    operationalLog
                );

            /*
             * -----------------------------------------------------
             * SHARED HTTP CLIENT
             *
             * Permanece necessário para /deals.
             * A página individual deixa de utilizar HTTP bruto.
             * -----------------------------------------------------
             */
            HttpClient httpClient =
                HttpClient.newBuilder()
                    .followRedirects(
                        HttpClient.Redirect.NORMAL
                    )
                    .build();

            /*
             * -----------------------------------------------------
             * AMAZON DEALS COLLECTION
             *
             * JavaHttpTransport
             *       ↓
             * HttpCollectionCollector
             *       ↓
             * AmazonDealsCollector
             * -----------------------------------------------------
             */
            JavaHttpTransport httpTransport =
                new JavaHttpTransport(
                    httpClient,
                    COLLECTION_REQUEST_TIMEOUT
                );

            HttpCollectionCollector genericCollector =
                new HttpCollectionCollector(
                    httpTransport,
                    clock,
                    COLLECTION_INTEGRATION,
                    observationRecorder,
                    failureClassifier
                );

            AmazonDealsCollector amazonDealsCollector =
                new AmazonDealsCollector(
                    genericCollector
                );

            /*
             * -----------------------------------------------------
             * AMAZON PARSING
             * -----------------------------------------------------
             */
            AmazonDealsParser dealsParser =
                new AmazonDealsParser();

            /*
             * -----------------------------------------------------
             * AMAZON PRODUCT PAGE ACQUISITION
             *
             * Conforme ADR-0014, a página individual é adquirida por
             * DOM renderizado.
             *
             * Existe uma única instância de Chromium para o lifecycle
             * do processo. Cada load() utiliza um BrowserContext
             * independente.
             * -----------------------------------------------------
             */
            productPageContentProvider =
                new PlaywrightRenderedProductPageContentProvider(
                    clock
                );

            /*
             * -----------------------------------------------------
             * AMAZON ENRICHMENT
             * -----------------------------------------------------
             */
            AmazonProductPageEnrichmentClient enrichmentClient =
                new AmazonProductPageEnrichmentClient(
                    productPageContentProvider,
                    new AmazonProductPageParser(),
                    new AmazonPaymentConditionParser(),
                    new AmazonCustomerReviewParser(),
                    clock,
                    ENRICHMENT_INTEGRATION,
                    observationRecorder,
                    failureClassifier
                );

            /*
             * -----------------------------------------------------
             * CONTINUOUS COMPOSITION
             * -----------------------------------------------------
             */
            ContinuousProcessingComposition.Settings settings =
                new ContinuousProcessingComposition.Settings(
                    runtimeConfig.scheduleKey(),
                    runtimeConfig.schedulerInstanceId(),
                    runtimeConfig.workerId(),
                    runtimeConfig.schedulerPollInterval(),
                    runtimeConfig.schedulerLeaseDuration(),
                    runtimeConfig.workerIdleDelay(),
                    runtimeConfig.collectionMaxAttempts(),
                    runtimeConfig.enrichmentMaxAttempts(),
                    runtimeConfig.evaluationMaxAttempts(),
                    runtimeConfig.retryBaseDelay(),
                    runtimeConfig.retryMaxDelay()
                );

            ContinuousProcessingComposition composition =
                new ContinuousProcessingComposition(
                    schedulerConnection,
                    workerConnection,
                    amazonDealsCollector,
                    dealsParser,
                    enrichmentClient,
                    operationalLog,
                    clock,
                    new ThreadSleepProcessingSchedulerWaitStrategy(),
                    new ThreadSleepWorkerIdleWaitStrategy(),
                    settings
                );

            ContinuousProcessingRuntime runtime =
                new ContinuousProcessingRuntime(
                    composition.schedulerRunner(),
                    composition.workerRunner()
                );

            ContinuousProcessingApplication application =
                new ContinuousProcessingApplication(
                    runtime,
                    schedulerConnection,
                    workerConnection,
                    List.of(
                        productPageContentProvider
                    )
                );

            /*
             * Ownership transferido para a aplicação.
             *
             * A partir daqui os catch blocks não podem mais fechar
             * browser nem Connections.
             */
            productPageContentProvider =
                null;

            schedulerConnection =
                null;

            workerConnection =
                null;

            return application;

        } catch (SQLException exception) {

            closeBestEffort(
                productPageContentProvider
            );

            closeBestEffort(
                workerConnection
            );

            closeBestEffort(
                schedulerConnection
            );

            throw new ContinuousProcessingBootstrapException(
                "Could not open continuous processing database connections",
                exception
            );

        } catch (RuntimeException exception) {

            closeBestEffort(
                productPageContentProvider
            );

            closeBestEffort(
                workerConnection
            );

            closeBestEffort(
                schedulerConnection
            );

            throw exception;
        }
    }

    private static void closeBestEffort(
        Connection connection
    ) {

        if (connection == null) {
            return;
        }

        try {

            connection.close();

        } catch (SQLException ignored) {

            /*
             * Estamos em rollback estrutural do bootstrap.
             * A causa original deve permanecer como causa principal.
             */
        }
    }

    private static void closeBestEffort(
        AutoCloseable resource
    ) {

        if (resource == null) {
            return;
        }

        try {

            resource.close();

        } catch (Exception ignored) {

            /*
             * Estamos desfazendo um bootstrap que já falhou.
             * Não escondemos a causa que iniciou o rollback.
             */
        }
    }
}
