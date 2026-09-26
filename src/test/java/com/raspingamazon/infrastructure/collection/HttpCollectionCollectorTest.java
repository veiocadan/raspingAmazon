package com.raspingamazon.infrastructure.collection;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.CollectionRequest;
import com.raspingamazon.application.collection.contract.HttpTransport;
import com.raspingamazon.application.collection.contract.HttpTransportResponse;
import com.raspingamazon.application.observability.IntegrationObservation;
import com.raspingamazon.application.observability.IntegrationObservationOutcome;
import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.observability.port.IntegrationObservationRecorder;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import com.raspingamazon.application.orchestration.failure.ProcessingFailureClassifier;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpCollectionCollectorTest {

    private static final URI SOURCE =
        URI.create(
            "https://example.com/deals"
        );

    private static final Instant COLLECTION_INSTANT =
        Instant.parse(
            "2026-09-16T22:00:00Z"
        );

    private static final Clock FIXED_CLOCK =
        Clock.fixed(
            COLLECTION_INSTANT,
            ZoneOffset.UTC
        );

    private static final String INTEGRATION =
        "amazon-deals-http";

    @Test
    void shouldTransformHttpResponseIntoCollectionResult() {

        HttpTransport transport =
            uri -> {

                assertEquals(
                    SOURCE,
                    uri
                );

                return new HttpTransportResponse(
                    200,
                    "payload"
                );
            };

        var collector =
            new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
            );

        var result =
            collector.collect(
                new CollectionRequest(
                    SOURCE
                )
            );

        assertEquals(
            "payload",
            result.content()
        );

        assertEquals(
            COLLECTION_INSTANT,
            result.collectedAt()
                .toInstant()
        );

        assertEquals(
            SOURCE.toString(),
            result.source()
        );
    }

    @Test
    void shouldPreserveHttpFailureStatusAndBodyExcerpt() {

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    503,
                    "<html>temporarily unavailable</html>"
                );

        var collector =
            new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
            );

        CollectionException exception =
            assertThrows(
                CollectionException.class,
                () -> collector.collect(
                    new CollectionRequest(
                        SOURCE
                    )
                )
            );

        assertEquals(
            503,
            exception.httpStatusCode()
        );

        assertEquals(
            "<html>temporarily unavailable</html>",
            exception.responseBodyExcerpt()
        );
    }

    @Test
    void shouldLimitHttpFailureBodyExcerpt() {

        String longBody =
            "x".repeat(
                3000
            );

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    503,
                    longBody
                );

        var collector =
            new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
            );

        CollectionException exception =
            assertThrows(
                CollectionException.class,
                () -> collector.collect(
                    new CollectionRequest(
                        SOURCE
                    )
                )
            );

        assertEquals(
            2000,
            exception.responseBodyExcerpt()
                .length()
        );
    }

    @Test
    void shouldPropagateCollectionExceptionFromTransport() {

        var expectedException =
            new CollectionException(
                "HTTP request failed"
            );

        HttpTransport transport =
            uri -> {
                throw expectedException;
            };

        var collector =
            new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
            );

        CollectionException exception =
            assertThrows(
                CollectionException.class,
                () -> collector.collect(
                    new CollectionRequest(
                        SOURCE
                    )
                )
            );

        assertSame(
            expectedException,
            exception
        );

        assertNull(
            exception.httpStatusCode()
        );
    }

    @Test
    void shouldRejectNullRequest() {

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    200,
                    "payload"
                );

        var collector =
            new HttpCollectionCollector(
                transport,
                FIXED_CLOCK
            );

        assertThrows(
            NullPointerException.class,
            () -> collector.collect(
                null
            )
        );
    }

    @Test
    void shouldRejectNullTransport() {

        assertThrows(
            NullPointerException.class,
            () -> new HttpCollectionCollector(
                null,
                FIXED_CLOCK
            )
        );
    }

    @Test
    void shouldRejectNullClock() {

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    200,
                    "payload"
                );

        assertThrows(
            NullPointerException.class,
            () -> new HttpCollectionCollector(
                transport,
                null
            )
        );
    }

    @Test
    void shouldRecordSuccessfulExternalInteractionWithTransportDuration() {

        RecordingObservationRecorder recorder =
            new RecordingObservationRecorder();

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    200,
                    "payload"
                );

        ProcessingFailureClassifier classifier =
            failure -> {
                throw new AssertionError(
                    "classifier must not run on success"
                );
            };

        HttpCollectionCollector collector =
            observedCollector(
                transport,
                recorder,
                classifier,
                nanoTime(
                    1_000_000_000L,
                    1_125_000_000L
                )
            );

        var result =
            collector.collect(
                new CollectionRequest(
                    SOURCE
                )
            );

        assertEquals(
            "payload",
            result.content()
        );

        assertEquals(
            1,
            recorder.observations.size()
        );

        IntegrationObservation observation =
            recorder.observations.getFirst();

        assertEquals(
            OffsetDateTime.ofInstant(
                COLLECTION_INSTANT,
                ZoneOffset.UTC
            ),
            observation.observedAt()
        );

        assertEquals(
            INTEGRATION,
            observation.integration()
        );

        assertEquals(
            "GET",
            observation.operation()
        );

        assertEquals(
            IntegrationObservationOutcome.SUCCESS,
            observation.outcome()
        );

        assertEquals(
            125L,
            observation.durationMs()
        );

        assertEquals(
            200,
            observation.httpStatusCode()
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
         * O collector não conhece nenhuma identidade do pipeline.
         */
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
            observation.context()
                .asin()
        );

        assertEquals(
            INTEGRATION,
            observation.context()
                .integration()
        );
    }

    @Test
    void shouldRecordRejectedHttpStatusUsingExistingFailureClassification() {

        RecordingObservationRecorder recorder =
            new RecordingObservationRecorder();

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    503,
                    "service unavailable"
                );

        ProcessingFailureClassifier classifier =
            failure -> {

                CollectionException collectionFailure =
                    (CollectionException) failure;

                assertEquals(
                    503,
                    collectionFailure.httpStatusCode()
                );

                return new FailureClassification(
                    ProcessingFailureType.TRANSIENT,
                    "COLLECTION_HTTP_503",
                    "HTTP 503"
                );
            };

        HttpCollectionCollector collector =
            observedCollector(
                transport,
                recorder,
                classifier,
                nanoTime(
                    2_000_000_000L,
                    2_450_000_000L
                )
            );

        CollectionException thrown =
            assertThrows(
                CollectionException.class,
                () -> collector.collect(
                    new CollectionRequest(
                        SOURCE
                    )
                )
            );

        assertEquals(
            503,
            thrown.httpStatusCode()
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
            450L,
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
            "COLLECTION_HTTP_503",
            observation.errorCode()
        );

        assertEquals(
            503,
            observation.httpStatusCode()
        );
    }

    @Test
    void shouldRecordTransportFailureAndRethrowSameCollectionException() {

        RecordingObservationRecorder recorder =
            new RecordingObservationRecorder();

        CollectionException expected =
            new CollectionException(
                "connection failed"
            );

        HttpTransport transport =
            uri -> {
                throw expected;
            };

        ProcessingFailureClassifier classifier =
            failure -> {

                assertSame(
                    expected,
                    failure
                );

                return new FailureClassification(
                    ProcessingFailureType.TRANSIENT,
                    "COLLECTION_TRANSPORT",
                    "connection failed"
                );
            };

        HttpCollectionCollector collector =
            observedCollector(
                transport,
                recorder,
                classifier,
                nanoTime(
                    3_000_000_000L,
                    3_750_000_000L
                )
            );

        CollectionException thrown =
            assertThrows(
                CollectionException.class,
                () -> collector.collect(
                    new CollectionRequest(
                        SOURCE
                    )
                )
            );

        /*
         * Observabilidade não substitui a exceção funcional.
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
            IntegrationObservationOutcome.FAILURE,
            observation.outcome()
        );

        assertEquals(
            750L,
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
            "COLLECTION_TRANSPORT",
            observation.errorCode()
        );

        assertNull(
            observation.httpStatusCode()
        );
    }

    @Test
    void shouldNotLetObservationFailureChangeSuccessfulCollection() {

        IntegrationObservationRecorder failingRecorder =
            observation -> {
                throw new IllegalStateException(
                    "observation unavailable"
                );
            };

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    200,
                    "payload"
                );

        ProcessingFailureClassifier classifier =
            failure -> {
                throw new AssertionError(
                    "classifier must not run on success"
                );
            };

        HttpCollectionCollector collector =
            observedCollector(
                transport,
                failingRecorder,
                classifier,
                nanoTime(
                    4_000_000_000L,
                    4_100_000_000L
                )
            );

        var result =
            assertDoesNotThrow(
                () -> collector.collect(
                    new CollectionRequest(
                        SOURCE
                    )
                )
            );

        assertEquals(
            "payload",
            result.content()
        );
    }

    @Test
    void shouldNotLetClassifierFailureReplaceOriginalCollectionFailure() {

        CollectionException expected =
            new CollectionException(
                "transport unavailable"
            );

        HttpTransport transport =
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

        HttpCollectionCollector collector =
            observedCollector(
                transport,
                recorder,
                failingClassifier,
                nanoTime(
                    5_000_000_000L,
                    5_050_000_000L
                )
            );

        CollectionException thrown =
            assertThrows(
                CollectionException.class,
                () -> collector.collect(
                    new CollectionRequest(
                        SOURCE
                    )
                )
            );

        assertSame(
            expected,
            thrown
        );
    }

    @Test
    void shouldRejectInvalidObservationDependencies() {

        HttpTransport transport =
            uri ->
                new HttpTransportResponse(
                    200,
                    "payload"
                );

        IntegrationObservationRecorder recorder =
            observation -> {
            };

        ProcessingFailureClassifier classifier =
            failure ->
                new FailureClassification(
                    ProcessingFailureType.PERMANENT,
                    "TEST_FAILURE",
                    "test failure"
                );

        LongSupplier nanoTime =
            () -> 0L;

        assertThrows(
            NullPointerException.class,
            () -> new HttpCollectionCollector(
                transport,
                FIXED_CLOCK,
                null,
                recorder,
                classifier,
                nanoTime
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new HttpCollectionCollector(
                transport,
                FIXED_CLOCK,
                " ",
                recorder,
                classifier,
                nanoTime
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> new HttpCollectionCollector(
                transport,
                FIXED_CLOCK,
                INTEGRATION,
                null,
                classifier,
                nanoTime
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> new HttpCollectionCollector(
                transport,
                FIXED_CLOCK,
                INTEGRATION,
                recorder,
                null,
                nanoTime
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> new HttpCollectionCollector(
                transport,
                FIXED_CLOCK,
                INTEGRATION,
                recorder,
                classifier,
                null
            )
        );
    }

    private HttpCollectionCollector observedCollector(
        HttpTransport transport,
        IntegrationObservationRecorder recorder,
        ProcessingFailureClassifier classifier,
        LongSupplier nanoTime
    ) {

        return new HttpCollectionCollector(
            transport,
            FIXED_CLOCK,
            INTEGRATION,
            recorder,
            classifier,
            nanoTime
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
}
