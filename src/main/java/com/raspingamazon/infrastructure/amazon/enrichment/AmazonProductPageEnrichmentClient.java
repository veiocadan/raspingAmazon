package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentClient;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.observability.IntegrationObservation;
import com.raspingamazon.application.observability.IntegrationObservationOutcome;
import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.observability.OperationalLogContext;
import com.raspingamazon.application.observability.port.IntegrationObservationRecorder;
import com.raspingamazon.application.orchestration.failure.DefaultProcessingFailureClassifier;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import com.raspingamazon.application.orchestration.failure.ProcessingFailureClassifier;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.commercial.PaymentCondition;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Cliente de enrichment baseado na página individual do produto.
 *
 * <p>A responsabilidade deste adaptador é:</p>
 *
 * <ol>
 *     <li>solicitar o conteúdo da página a um provider;</li>
 *     <li>interpretar seller e delivery;</li>
 *     <li>interpretar rating e reviewCount;</li>
 *     <li>interpretar condições comerciais;</li>
 *     <li>montar o contrato normalizado de enrichment.</li>
 * </ol>
 *
 * <p>Todos os parsers recebem exatamente o mesmo HTML adquirido pelo
 * ProductPageContentProvider. Não existe segunda chamada HTTP para
 * rating/reviewCount.</p>
 *
 * <p>O mecanismo utilizado para adquirir o conteúdo da página fica
 * deliberadamente separado desta classe. Assim, HTTP bruto e DOM
 * renderizado podem ser estratégias substituíveis sem alterar
 * parsers ou regras de negócio.</p>
 *
 * <p>Quando observabilidade explícita é configurada, somente a chamada
 * a ProductPageContentProvider.load() é considerada a operação da
 * integração externa. Parsing, normalização e montagem do resultado
 * permanecem fora dessa medição.</p>
 *
 * <p>Esta classe não decide elegibilidade, filtros, score ou
 * publicação.</p>
 */
public final class AmazonProductPageEnrichmentClient
    implements ProductEnrichmentClient {

    private static final String SOURCE =
        "AMAZON_PRODUCT_PAGE";

    private static final String DEFAULT_INTEGRATION =
        "amazon-product-page";

    private static final String OBSERVED_OPERATION =
        "LOAD";

    private static final IntegrationObservationRecorder
        NO_OP_OBSERVATION_RECORDER =
        observation -> {
        };

    private final ProductPageContentProvider
        contentProvider;

    private final AmazonProductPageParser
        parser;

    private final AmazonPaymentConditionParser
        paymentConditionParser;

    private final AmazonCustomerReviewParser
        customerReviewParser;

    private final Clock clock;

    private final String integration;

    private final IntegrationObservationRecorder
        observationRecorder;

    private final ProcessingFailureClassifier
        failureClassifier;

    private final LongSupplier nanoTime;

    /**
     * Construtor padrão utilizado pela aplicação.
     *
     * <p>Preserva compatibilidade histórica. A observabilidade somente é
     * ativada quando o composition root fornece explicitamente um
     * IntegrationObservationRecorder.</p>
     */
    public AmazonProductPageEnrichmentClient() {

        this(
            new HttpProductPageContentProvider(),
            new AmazonProductPageParser(),
            new AmazonPaymentConditionParser(),
            new AmazonCustomerReviewParser(),
            Clock.systemUTC(),
            DEFAULT_INTEGRATION,
            NO_OP_OBSERVATION_RECORDER,
            new DefaultProcessingFailureClassifier(),
            System::nanoTime
        );
    }

    /**
     * Construtor de compatibilidade utilizado pela composition e
     * pelos testes existentes.
     */
    public AmazonProductPageEnrichmentClient(
        HttpClient httpClient,
        AmazonProductPageParser parser
    ) {

        this(
            new HttpProductPageContentProvider(
                httpClient
            ),
            parser,
            new AmazonPaymentConditionParser(),
            new AmazonCustomerReviewParser(),
            Clock.systemUTC(),
            DEFAULT_INTEGRATION,
            NO_OP_OBSERVATION_RECORDER,
            new DefaultProcessingFailureClassifier(),
            System::nanoTime
        );
    }

    /**
     * Construtor de compatibilidade para consumidores que injetam
     * explicitamente os parsers já existentes.
     */
    public AmazonProductPageEnrichmentClient(
        HttpClient httpClient,
        AmazonProductPageParser parser,
        AmazonPaymentConditionParser paymentConditionParser
    ) {

        this(
            new HttpProductPageContentProvider(
                httpClient
            ),
            parser,
            paymentConditionParser,
            new AmazonCustomerReviewParser(),
            Clock.systemUTC(),
            DEFAULT_INTEGRATION,
            NO_OP_OBSERVATION_RECORDER,
            new DefaultProcessingFailureClassifier(),
            System::nanoTime
        );
    }

    /**
     * Construtor estrutural de compatibilidade para providers
     * alternativos já existentes.
     */
    public AmazonProductPageEnrichmentClient(
        ProductPageContentProvider contentProvider,
        AmazonProductPageParser parser,
        AmazonPaymentConditionParser paymentConditionParser
    ) {

        this(
            contentProvider,
            parser,
            paymentConditionParser,
            new AmazonCustomerReviewParser(),
            Clock.systemUTC(),
            DEFAULT_INTEGRATION,
            NO_OP_OBSERVATION_RECORDER,
            new DefaultProcessingFailureClassifier(),
            System::nanoTime
        );
    }

    /**
     * Construtor completo histórico.
     */
    public AmazonProductPageEnrichmentClient(
        ProductPageContentProvider contentProvider,
        AmazonProductPageParser parser,
        AmazonPaymentConditionParser paymentConditionParser,
        AmazonCustomerReviewParser customerReviewParser
    ) {

        this(
            contentProvider,
            parser,
            paymentConditionParser,
            customerReviewParser,
            Clock.systemUTC(),
            DEFAULT_INTEGRATION,
            NO_OP_OBSERVATION_RECORDER,
            new DefaultProcessingFailureClassifier(),
            System::nanoTime
        );
    }

    /**
     * Construtor completo com observabilidade explícita.
     *
     * <p>Este é o ponto destinado ao composition root de produção da
     * FASE 16.</p>
     *
     * @param contentProvider fronteira concreta de aquisição da página
     * @param parser parser de seller/delivery
     * @param paymentConditionParser parser de condições comerciais
     * @param customerReviewParser parser de rating/reviewCount
     * @param clock relógio utilizado para timestamp da observação
     * @param integration identificador estável da integração
     * @param observationRecorder recorder best-effort
     * @param failureClassifier classificador funcional já existente
     */
    public AmazonProductPageEnrichmentClient(
        ProductPageContentProvider contentProvider,
        AmazonProductPageParser parser,
        AmazonPaymentConditionParser paymentConditionParser,
        AmazonCustomerReviewParser customerReviewParser,
        Clock clock,
        String integration,
        IntegrationObservationRecorder observationRecorder,
        ProcessingFailureClassifier failureClassifier
    ) {

        this(
            contentProvider,
            parser,
            paymentConditionParser,
            customerReviewParser,
            clock,
            integration,
            observationRecorder,
            failureClassifier,
            System::nanoTime
        );
    }

    /**
     * Variante package-private com fonte monotônica de tempo injetável.
     *
     * <p>Clock representa tempo civil. nanoTime representa somente
     * duração e não sofre ajustes do relógio do sistema.</p>
     */
    AmazonProductPageEnrichmentClient(
        ProductPageContentProvider contentProvider,
        AmazonProductPageParser parser,
        AmazonPaymentConditionParser paymentConditionParser,
        AmazonCustomerReviewParser customerReviewParser,
        Clock clock,
        String integration,
        IntegrationObservationRecorder observationRecorder,
        ProcessingFailureClassifier failureClassifier,
        LongSupplier nanoTime
    ) {

        this.contentProvider =
            Objects.requireNonNull(
                contentProvider,
                "contentProvider must not be null"
            );

        this.parser =
            Objects.requireNonNull(
                parser,
                "parser must not be null"
            );

        this.paymentConditionParser =
            Objects.requireNonNull(
                paymentConditionParser,
                "paymentConditionParser must not be null"
            );

        this.customerReviewParser =
            Objects.requireNonNull(
                customerReviewParser,
                "customerReviewParser must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        this.integration =
            requireText(
                integration,
                "integration must not be blank"
            );

        this.observationRecorder =
            Objects.requireNonNull(
                observationRecorder,
                "observationRecorder must not be null"
            );

        this.failureClassifier =
            Objects.requireNonNull(
                failureClassifier,
                "failureClassifier must not be null"
            );

        this.nanoTime =
            Objects.requireNonNull(
                nanoTime,
                "nanoTime must not be null"
            );
    }

    /**
     * Enriquece uma oferta previamente interpretada pela etapa de Deals.
     */
    @Override
    public ProductEnrichmentResult enrich(
        ParsedDeal parsedDeal
    ) {

        return enrich(
            parsedDeal,
            OperationalLogContext.empty()
        );
    }

    @Override
    public ProductEnrichmentResult enrich(
        ParsedDeal parsedDeal,
        OperationalLogContext context
    ) {

        Objects.requireNonNull(
            parsedDeal,
            "Parsed deal must not be null"
        );

        Objects.requireNonNull(
            context,
            "Operational log context must not be null"
        );

        String productUrl =
            parsedDeal.productUrl();

        if (productUrl == null
            || productUrl.isBlank()) {

            throw new ProductEnrichmentException(
                "Parsed deal does not contain a product URL"
            );
        }

        URI uri;

        try {

            uri =
                URI.create(
                    productUrl
                );

        } catch (IllegalArgumentException exception) {

            throw new ProductEnrichmentException(
                "Parsed deal contains an invalid product URL",
                exception
            );
        }

        OperationalLogContext observationContext =
            observationContext(
                context,
                parsedDeal.asin()
            );

        ProductPageContent pageContent;

        /*
         * A medição começa imediatamente antes da fronteira de aquisição.
         *
         * Nenhum parser ou regra comercial participa desta duração.
         */
        long startedAtNanos =
            nanoTime.getAsLong();

        try {

            pageContent =
                contentProvider.load(
                    uri
                );

        } catch (ProductPageContentProviderException exception) {

            long durationMs =
                elapsedMilliseconds(
                    startedAtNanos
                );

            /*
             * ProductPageContentProviderException é o contrato explícito
             * de falha técnica da aquisição da página.
             *
             * A origem observacional é EXTERNAL.
             *
             * TRANSIENT/PERMANENT continua vindo do mesmo classificador
             * funcional que o restante do pipeline já utiliza. Esta
             * instrumentação não introduz uma nova política de retry.
             */
            recordFailureBestEffort(
                exception,
                OperationalFailureOrigin.EXTERNAL,
                durationMs,
                observationContext
            );

            /*
             * Preserva exatamente a semântica funcional histórica.
             */
            throw new ProductEnrichmentException(
                "Failed to retrieve Amazon product page",
                exception
            );

        } catch (RuntimeException exception) {

            long durationMs =
                elapsedMilliseconds(
                    startedAtNanos
                );

            /*
             * Uma RuntimeException diferente do contrato técnico do
             * provider continua sendo um erro inesperado interno.
             *
             * Ela é observada como INTERNAL e depois a mesma instância
             * é relançada, preservando o comportamento anterior.
             */
            recordFailureBestEffort(
                exception,
                OperationalFailureOrigin.INTERNAL,
                durationMs,
                observationContext
            );

            throw exception;
        }

        /*
         * O marcador final é obtido imediatamente após o retorno do
         * provider. Tudo abaixo fica fora da latência da integração.
         */
        long durationMs =
            elapsedMilliseconds(
                startedAtNanos
            );

        recordSuccessBestEffort(
            durationMs,
            observationContext
        );

        String html =
            pageContent.html();

        AmazonProductPageParser.ParsedProductOffer parsed =
            parser.parse(
                html
            );

        AmazonCustomerReviewParser.ParsedCustomerReviews
            customerReviews =
            customerReviewParser.parse(
                html,
                parsedDeal.asin()
            );

        List<PaymentCondition> paymentConditions =
            paymentConditionParser.parse(
                html
            );

        return new ProductEnrichmentResult(
            parsedDeal.asin(),
            parsed.sellerEvidence(),
            parsed.deliveryEvidence(),
            customerReviews.ratingEvidence(),
            customerReviews.reviewCountEvidence(),
            paymentConditions,
            SOURCE,
            productUrl,
            pageContent.collectedAt()
        );
    }

    /**
     * Registra aquisição externa concluída.
     *
     * <p>O provider é abstrato e também pode representar uma estratégia
     * de navegador renderizado. Por isso não inventamos um status HTTP
     * quando o contrato ProductPageContent não fornece essa informação.</p>
     */
    private void recordSuccessBestEffort(
        long durationMs,
        OperationalLogContext context
    ) {

        try {

            observationRecorder.record(
                new IntegrationObservation(
                    null,
                    OffsetDateTime.now(
                        clock
                    ),
                    integration,
                    OBSERVED_OPERATION,
                    IntegrationObservationOutcome.SUCCESS,
                    durationMs,
                    context,
                    null,
                    null,
                    null,
                    null
                )
            );

        } catch (RuntimeException ignored) {

            /*
             * A telemetria nunca pode transformar enrichment válido em
             * falha funcional.
             */
        }
    }

    /**
     * Registra falha observada da aquisição da página.
     *
     * <p>O failureType e o errorCode refletem a política funcional atual.
     * Esta classe não cria uma segunda taxonomia e não modifica retry.</p>
     */
    private void recordFailureBestEffort(
        Throwable failure,
        OperationalFailureOrigin failureOrigin,
        long durationMs,
        OperationalLogContext context
    ) {

        try {

            FailureClassification classification =
                Objects.requireNonNull(
                    failureClassifier.classify(
                        failure
                    ),
                    "Failure classifier must not return null"
                );

            observationRecorder.record(
                new IntegrationObservation(
                    null,
                    OffsetDateTime.now(
                        clock
                    ),
                    integration,
                    OBSERVED_OPERATION,
                    IntegrationObservationOutcome.FAILURE,
                    durationMs,
                    context,
                    failureOrigin,
                    classification.type(),
                    classification.code(),
                    null
                )
            );

        } catch (RuntimeException ignored) {

            /*
             * Falha na classificação ou no recorder não substitui a
             * exceção funcional original.
             */
        }
    }

    /**
     * Preserva a correlação fornecida pela aplicação e acrescenta os
     * atributos que pertencem a esta fronteira concreta.
     *
     * <p>O ASIN vem do ParsedDeal efetivamente enriquecido. O nome da
     * integração pertence ao adapter. Identidades que ainda não existem,
     * como snapshotId, continuam ausentes em vez de serem inferidas.</p>
     */
    private OperationalLogContext observationContext(
        OperationalLogContext context,
        String asin
    ) {

        return new OperationalLogContext(
            context.runId(),
            context.jobId(),
            context.jobType(),
            context.candidateId(),
            context.snapshotId(),
            context.evaluationId(),
            context.publicationId(),
            asin,
            integration
        );
    }

    private long elapsedMilliseconds(
        long startedAtNanos
    ) {

        long finishedAtNanos =
            nanoTime.getAsLong();

        long elapsedNanos =
            finishedAtNanos
                - startedAtNanos;

        if (elapsedNanos <= 0L) {
            return 0L;
        }

        return TimeUnit.NANOSECONDS
            .toMillis(
                elapsedNanos
            );
    }

    private static String requireText(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }

    /**
     * Erro específico da operação de enrichment.
     */
    public static final class ProductEnrichmentException
        extends RuntimeException {

        public ProductEnrichmentException(
            String message
        ) {

            super(
                message
            );
        }

        public ProductEnrichmentException(
            String message,
            Throwable cause
        ) {

            super(
                message,
                cause
            );
        }
    }
}
