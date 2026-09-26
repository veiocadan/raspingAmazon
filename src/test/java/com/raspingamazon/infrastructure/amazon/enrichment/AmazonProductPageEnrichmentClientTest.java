package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.observability.IntegrationObservation;
import com.raspingamazon.application.observability.IntegrationObservationOutcome;
import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.observability.port.IntegrationObservationRecorder;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import com.raspingamazon.application.orchestration.failure.ProcessingFailureClassifier;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Testes do client de enrichment da página individual do produto.
 *
 * <p>O servidor HTTP utilizado é local, portanto estes testes continuam
 * herméticos e não acessam a Amazon real.</p>
 *
 * <p>A resposta de sucesso utiliza uma fixture mínima contendo somente
 * as estruturas realmente consumidas pelos parsers.</p>
 */
class AmazonProductPageEnrichmentClientTest {

    private static final String INTEGRATION =
        "amazon-product-page";

    private static final Instant OBSERVATION_INSTANT =
        Instant.parse(
            "2026-09-26T17:00:00Z"
        );

    private static final Clock FIXED_CLOCK =
        Clock.fixed(
            OBSERVATION_INSTANT,
            ZoneOffset.UTC
        );

    @Test
    void shouldEnrichParsedDealFromAmazonProductPage()
        throws Exception {

        String html =
            loadFixture(
                "amazon-amazon.html"
            );

        try (TestHttpServer server =
                 TestHttpServer.start(
                     200,
                     html
                 )) {

            AmazonProductPageEnrichmentClient client =
                new AmazonProductPageEnrichmentClient(
                    HttpClient.newHttpClient(),
                    new AmazonProductPageParser()
                );

            ParsedDeal parsedDeal =
                createParsedDeal(
                    server.url()
                );

            ProductEnrichmentResult result =
                client.enrich(
                    parsedDeal
                );

            assertNotNull(
                result
            );
        }
    }

    @Test
    void shouldRejectHttpError()
        throws Exception {

        try (TestHttpServer server =
                 TestHttpServer.start(
                     503,
                     "Service unavailable"
                 )) {

            AmazonProductPageEnrichmentClient client =
                new AmazonProductPageEnrichmentClient(
                    HttpClient.newHttpClient(),
                    new AmazonProductPageParser()
                );

            assertThrows(
                AmazonProductPageEnrichmentClient
                    .ProductEnrichmentException.class,
                () -> client.enrich(
                    createParsedDeal(
                        server.url()
                    )
                )
            );
        }
    }

    @Test
    void shouldRejectEmptyResponse()
        throws Exception {

        try (TestHttpServer server =
                 TestHttpServer.start(
                     200,
                     "   "
                 )) {

            AmazonProductPageEnrichmentClient client =
                new AmazonProductPageEnrichmentClient(
                    HttpClient.newHttpClient(),
                    new AmazonProductPageParser()
                );

            assertThrows(
                AmazonProductPageEnrichmentClient
                    .ProductEnrichmentException.class,
                () -> client.enrich(
                    createParsedDeal(
                        server.url()
                    )
                )
            );
        }
    }

    @Test
    void shouldRejectMissingProductUrl() {

        ParsedDeal parsedDeal =
            createParsedDeal(
                null
            );

        AmazonProductPageEnrichmentClient client =
            new AmazonProductPageEnrichmentClient(
                HttpClient.newHttpClient(),
                new AmazonProductPageParser()
            );

        assertThrows(
            AmazonProductPageEnrichmentClient
                .ProductEnrichmentException.class,
            () -> client.enrich(
                parsedDeal
            )
        );
    }

    @Test
    void shouldWrapTransportFailure() {

        AmazonProductPageEnrichmentClient client =
            new AmazonProductPageEnrichmentClient(
                HttpClient.newHttpClient(),
                new AmazonProductPageParser()
            );

        ParsedDeal parsedDeal =
            createParsedDeal(
                "http://127.0.0.1:1/product"
            );

        assertThrows(
            AmazonProductPageEnrichmentClient
                .ProductEnrichmentException.class,
            () -> client.enrich(
                parsedDeal
            )
        );
    }

    @Test
    void shouldRecordSuccessfulProductPageAcquisitionWithAsin()
        throws Exception {

        String html =
            loadFixture(
                "amazon-amazon.html"
            );

        RecordingObservationRecorder recorder =
            new RecordingObservationRecorder();

        ProcessingFailureClassifier classifier =
            failure -> {
                throw new AssertionError(
                    "classifier must not run on acquisition success"
                );
            };

        ProductPageContentProvider provider =
            uri ->
                new ProductPageContent(
                    uri,
                    uri,
                    html,
                    OffsetDateTime.parse(
                        "2026-09-26T16:59:00Z"
                    )
                );

        AmazonProductPageEnrichmentClient client =
            observedClient(
                provider,
                recorder,
                classifier,
                nanoTime(
                    1_000_000_000L,
                    1_325_000_000L
                )
            );

        ProductEnrichmentResult result =
            client.enrich(
                createParsedDeal(
                    "https://example.com/product"
                )
            );

        assertNotNull(
            result
        );

        assertEquals(
            1,
            recorder.observations.size()
        );

        IntegrationObservation observation =
            recorder.observations.getFirst();

        assertEquals(
            OffsetDateTime.ofInstant(
                OBSERVATION_INSTANT,
                ZoneOffset.UTC
            ),
            observation.observedAt()
        );

        assertEquals(
            INTEGRATION,
            observation.integration()
        );

        assertEquals(
            "LOAD",
            observation.operation()
        );

        assertEquals(
            IntegrationObservationOutcome.SUCCESS,
            observation.outcome()
        );

        assertEquals(
            325L,
            observation.durationMs()
        );

        assertEquals(
            "B000000001",
            observation.context()
                .asin()
        );

        assertEquals(
            INTEGRATION,
            observation.context()
                .integration()
        );

        assertNull(
            observation.context()
                .runId()
        );

        assertNull(
            observation.context()
                .jobId()
        );

        assertNull(
            observation.context()
                .candidateId()
        );

        assertNull(
            observation.failureOrigin()
        );

        assertNull(
            observation.failureType()
        );

        assertNull(
            observation.errorCode()
        );

        /*
         * ProductPageContentProvider é uma abstração de aquisição e
         * não promete expor status HTTP.
         */
        assertNull(
            observation.httpStatusCode()
        );
    }

    @Test
    void shouldRecordProviderFailureAsExternalAndPreserveWrapping()
        throws Exception {

        ProductPageContentProviderException expected =
            new ProductPageContentProviderException(
                "provider timeout",
                new HttpTimeoutException(
                    "timeout"
                )
            );

        ProductPageContentProvider provider =
            uri -> {
                throw expected;
            };

        RecordingObservationRecorder recorder =
            new RecordingObservationRecorder();

        ProcessingFailureClassifier classifier =
            failure -> {

                assertSame(
                    expected,
                    failure
                );

                return new FailureClassification(
                    ProcessingFailureType.TRANSIENT,
                    "NETWORK_TIMEOUT",
                    "timeout"
                );
            };

        AmazonProductPageEnrichmentClient client =
            observedClient(
                provider,
                recorder,
                classifier,
                nanoTime(
                    2_000_000_000L,
                    2_700_000_000L
                )
            );

        AmazonProductPageEnrichmentClient
            .ProductEnrichmentException thrown =
            assertThrows(
                AmazonProductPageEnrichmentClient
                    .ProductEnrichmentException.class,
                () -> client.enrich(
                    createParsedDeal(
                        "https://example.com/product"
                    )
                )
            );

        /*
         * A semântica funcional histórica continua intacta.
         */
        assertSame(
            expected,
            thrown.getCause()
        );

        assertEquals(
            1,
            recorder.observations.size()
        );

        IntegrationObservation observation =
            recorder.observations.getFirst();

        assertEquals(
            IntegrationObservationOutcome.FAILURE,
            observation.outcome()
        );

        assertEquals(
            700L,
            observation.durationMs()
        );

        assertEquals(
            OperationalFailureOrigin.EXTERNAL,
            observation.failureOrigin()
        );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            observation.failureType()
        );

        assertEquals(
            "NETWORK_TIMEOUT",
            observation.errorCode()
        );

        assertEquals(
            "B000000001",
            observation.context()
                .asin()
        );

        assertNull(
            observation.httpStatusCode()
        );
    }

    @Test
    void shouldRecordUnexpectedProviderFailureAsInternalAndRethrowSameFailure() {

        IllegalStateException expected =
            new IllegalStateException(
                "unexpected provider state"
            );

        ProductPageContentProvider provider =
            uri -> {
                throw expected;
            };

        RecordingObservationRecorder recorder =
            new RecordingObservationRecorder();

        ProcessingFailureClassifier classifier =
            failure -> {

                assertSame(
                    expected,
                    failure
                );

                return new FailureClassification(
                    ProcessingFailureType.PERMANENT,
                    "INVALID_PROCESSING_STATE",
                    "unexpected provider state"
                );
            };

        AmazonProductPageEnrichmentClient client =
            observedClient(
                provider,
                recorder,
                classifier,
                nanoTime(
                    3_000_000_000L,
                    3_090_000_000L
                )
            );

        IllegalStateException thrown =
            assertThrows(
                IllegalStateException.class,
                () -> client.enrich(
                    createParsedDeal(
                        "https://example.com/product"
                    )
                )
            );

        /*
         * A instrumentação não altera o comportamento funcional
         * preexistente de RuntimeException inesperada.
         */
        assertSame(
            expected,
            thrown
        );

        assertEquals(
            1,
            recorder.observations.size()
        );

        IntegrationObservation observation =
            recorder.observations.getFirst();

        assertEquals(
            OperationalFailureOrigin.INTERNAL,
            observation.failureOrigin()
        );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            observation.failureType()
        );

        assertEquals(
            "INVALID_PROCESSING_STATE",
            observation.errorCode()
        );

        assertEquals(
            90L,
            observation.durationMs()
        );
    }

    @Test
    void shouldNotLetObservationFailureChangeSuccessfulEnrichment()
        throws Exception {

        String html =
            loadFixture(
                "amazon-amazon.html"
            );

        ProductPageContentProvider provider =
            uri ->
                new ProductPageContent(
                    uri,
                    uri,
                    html,
                    OffsetDateTime.parse(
                        "2026-09-26T16:59:00Z"
                    )
                );

        IntegrationObservationRecorder failingRecorder =
            observation -> {
                throw new IllegalStateException(
                    "observability unavailable"
                );
            };

        ProcessingFailureClassifier classifier =
            failure -> {
                throw new AssertionError(
                    "classifier must not run on acquisition success"
                );
            };

        AmazonProductPageEnrichmentClient client =
            observedClient(
                provider,
                failingRecorder,
                classifier,
                nanoTime(
                    4_000_000_000L,
                    4_050_000_000L
                )
            );

        ProductEnrichmentResult result =
            client.enrich(
                createParsedDeal(
                    "https://example.com/product"
                )
            );

        assertNotNull(
            result
        );
    }

    @Test
    void shouldNotLetClassifierFailureReplaceProviderFailure() {

        ProductPageContentProviderException expected =
            new ProductPageContentProviderException(
                "provider unavailable"
            );

        ProductPageContentProvider provider =
            uri -> {
                throw expected;
            };

        ProcessingFailureClassifier failingClassifier =
            failure -> {
                throw new IllegalStateException(
                    "classifier unavailable"
                );
            };

        IntegrationObservationRecorder recorder =
            observation -> {
                throw new AssertionError(
                    "recorder must not run when classification fails"
                );
            };

        AmazonProductPageEnrichmentClient client =
            observedClient(
                provider,
                recorder,
                failingClassifier,
                nanoTime(
                    5_000_000_000L,
                    5_150_000_000L
                )
            );

        AmazonProductPageEnrichmentClient
            .ProductEnrichmentException thrown =
            assertThrows(
                AmazonProductPageEnrichmentClient
                    .ProductEnrichmentException.class,
                () -> client.enrich(
                    createParsedDeal(
                        "https://example.com/product"
                    )
                )
            );

        assertSame(
            expected,
            thrown.getCause()
        );
    }

    /**
     * Cria client instrumentado com fonte monotônica determinística.
     */
    private AmazonProductPageEnrichmentClient observedClient(
        ProductPageContentProvider provider,
        IntegrationObservationRecorder recorder,
        ProcessingFailureClassifier classifier,
        LongSupplier nanoTime
    ) {

        return new AmazonProductPageEnrichmentClient(
            provider,
            new AmazonProductPageParser(),
            new AmazonPaymentConditionParser(),
            new AmazonCustomerReviewParser(),
            FIXED_CLOCK,
            INTEGRATION,
            recorder,
            classifier,
            nanoTime
        );
    }

    /**
     * Cria um ParsedDeal mínimo suficiente para os testes de enrichment.
     *
     * <p>rating e reviewCount são deliberadamente null porque este arquivo
     * testa somente a etapa posterior de seller/delivery.</p>
     */
    private ParsedDeal createParsedDeal(
        String productUrl
    ) {

        return new ParsedDeal(
            "B000000001",
            productUrl,
            "Produto de teste",
            "https://example.com/image.jpg",

            new BigDecimal(
                "100.00"
            ),

            new BigDecimal(
                "120.00"
            ),

            /*
             * previousPrice
             */
            null,

            /*
             * soldPercentage
             */
            null,

            /*
             * rating
             */
            null,

            /*
             * reviewCount
             */
            null,

            OffsetDateTime.parse(
                "2026-09-26T16:00:00Z"
            ),

            "AMAZON_DEALS"
        );
    }

    private LongSupplier nanoTime(
        long... values
    ) {

        AtomicInteger index =
            new AtomicInteger();

        return () -> {

            int current =
                index.getAndIncrement();

            if (current >= values.length) {

                throw new AssertionError(
                    "nanoTime called more times than expected"
                );
            }

            return values[current];
        };
    }

    /**
     * Carrega fixture mínima de página de produto.
     */
    private String loadFixture(
        String fileName
    ) throws Exception {

        String resourcePath =
            "/amazon/fixtures/product/"
                + fileName;

        try (var inputStream =
                 getClass()
                     .getResourceAsStream(
                         resourcePath
                     )) {

            if (inputStream == null) {
                throw new IllegalStateException(
                    "Fixture not found: "
                        + resourcePath
                );
            }

            return new String(
                inputStream.readAllBytes(),
                StandardCharsets.UTF_8
            );
        }
    }

    /**
     * Recorder em memória utilizado somente para observar os fatos
     * produzidos pelo teste.
     */
    private static final class RecordingObservationRecorder
        implements IntegrationObservationRecorder {

        private final List<IntegrationObservation> observations =
            new ArrayList<>();

        @Override
        public void record(
            IntegrationObservation observation
        ) {

            observations.add(
                observation
            );
        }
    }

    /**
     * Servidor HTTP local usado para tornar o teste hermético.
     */
    private static final class TestHttpServer
        implements AutoCloseable {

        private final HttpServer server;

        private final ExecutorService executor;

        private TestHttpServer(
            HttpServer server,
            ExecutorService executor
        ) {
            this.server =
                server;

            this.executor =
                executor;
        }

        static TestHttpServer start(
            int statusCode,
            String body
        ) throws Exception {

            HttpServer server =
                HttpServer.create(
                    new InetSocketAddress(
                        "127.0.0.1",
                        0
                    ),
                    0
                );

            server.createContext(
                "/product",
                exchange -> {

                    byte[] response =
                        body.getBytes(
                            StandardCharsets.UTF_8
                        );

                    exchange.sendResponseHeaders(
                        statusCode,
                        response.length
                    );

                    try (var output =
                             exchange.getResponseBody()) {

                        output.write(
                            response
                        );
                    }
                }
            );

            ExecutorService executor =
                Executors.newCachedThreadPool();

            server.setExecutor(
                executor
            );

            server.start();

            return new TestHttpServer(
                server,
                executor
            );
        }

        String url() {

            return "http://127.0.0.1:"
                + server.getAddress()
                .getPort()
                + "/product";
        }

        @Override
        public void close() {

            server.stop(
                0
            );

            executor.shutdownNow();
        }
    }
}
