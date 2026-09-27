package com.raspingamazon.application.observability;

import com.raspingamazon.application.observability.port.IntegrationObservationPersistencePort;
import com.raspingamazon.application.observability.port.StructuredOperationalLogPort;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import com.raspingamazon.application.orchestration.failure.ProcessingFailureClassifier;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BestEffortIntegrationObservationRecorderTest {

    private static final OffsetDateTime OBSERVED_AT =
        OffsetDateTime.parse(
            "2026-09-26T14:00:00Z"
        );

    @Test
    void shouldPersistObservationWithoutProducingFailureLog() {

        RecordingPersistence persistence =
            new RecordingPersistence();

        RecordingLog log =
            new RecordingLog();

        ProcessingFailureClassifier classifier =
            failure -> {
                throw new AssertionError(
                    "classifier must not run on persistence success"
                );
            };

        BestEffortIntegrationObservationRecorder recorder =
            new BestEffortIntegrationObservationRecorder(
                persistence,
                classifier,
                log
            );

        IntegrationObservation observation =
            successfulObservation();

        recorder.record(
            observation
        );

        assertEquals(
            1,
            persistence.calls.get()
        );

        assertEquals(
            observation,
            persistence.lastObservation
        );

        assertEquals(
            0,
            log.events.size()
        );
    }

    @Test
    void shouldSwallowPersistenceFailureAndLogItAsInternal() {

        RuntimeException persistenceFailure =
            new RuntimeException(
                "database unavailable"
            );

        IntegrationObservationPersistencePort persistence =
            observation -> {
                throw persistenceFailure;
            };

        ProcessingFailureClassifier classifier =
            failure -> {

                assertEquals(
                    persistenceFailure,
                    failure
                );

                return new FailureClassification(
                    ProcessingFailureType.TRANSIENT,
                    "DATABASE_TRANSIENT",
                    "database unavailable"
                );
            };

        RecordingLog log =
            new RecordingLog();

        BestEffortIntegrationObservationRecorder recorder =
            new BestEffortIntegrationObservationRecorder(
                persistence,
                classifier,
                log
            );

        IntegrationObservation observation =
            failedExternalObservation();

        assertDoesNotThrow(
            () -> recorder.record(
                observation
            )
        );

        assertEquals(
            1,
            log.events.size()
        );

        OperationalLogEvent event =
            log.events.getFirst();

        assertEquals(
            OperationalLogLevel.ERROR,
            event.level()
        );

        assertEquals(
            "integration.observation.persistence-failed",
            event.event()
        );

        assertEquals(
            "integration-observation-recorder",
            event.component()
        );

        assertEquals(
            "persist-observation",
            event.operation()
        );

        assertEquals(
            "FAILURE",
            event.outcome()
        );

        assertEquals(
            OperationalFailureOrigin.INTERNAL,
            event.failureOrigin()
        );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            event.failureType()
        );

        assertEquals(
            "DATABASE_TRANSIENT",
            event.errorCode()
        );

        assertEquals(
            101L,
            event.context()
                .runId()
        );

        assertEquals(
            202L,
            event.context()
                .jobId()
        );

        assertEquals(
            ProcessingJobType.COLLECT_DEALS,
            event.context()
                .jobType()
        );

        assertEquals(
            "B0OBSERVE01",
            event.context()
                .asin()
        );

        assertEquals(
            "amazon-deals-http",
            event.context()
                .integration()
        );

        assertNull(
            event.durationMs()
        );
    }

    @Test
    void shouldNotPropagateClassifierFailure() {

        IntegrationObservationPersistencePort persistence =
            observation -> {
                throw new RuntimeException(
                    "persistence failed"
                );
            };

        ProcessingFailureClassifier classifier =
            failure -> {
                throw new IllegalStateException(
                    "classifier failed"
                );
            };

        StructuredOperationalLogPort log =
            event -> {
                throw new AssertionError(
                    "log must not be reached when classifier fails"
                );
            };

        BestEffortIntegrationObservationRecorder recorder =
            new BestEffortIntegrationObservationRecorder(
                persistence,
                classifier,
                log
            );

        assertDoesNotThrow(
            () -> recorder.record(
                successfulObservation()
            )
        );
    }

    @Test
    void shouldNotPropagateLoggingFailure() {

        IntegrationObservationPersistencePort persistence =
            observation -> {
                throw new RuntimeException(
                    "persistence failed"
                );
            };

        ProcessingFailureClassifier classifier =
            failure ->
                new FailureClassification(
                    ProcessingFailureType.PERMANENT,
                    "OBSERVABILITY_STORAGE_FAILURE",
                    "persistence failed"
                );

        StructuredOperationalLogPort log =
            event -> {
                throw new IllegalStateException(
                    "logging failed"
                );
            };

        BestEffortIntegrationObservationRecorder recorder =
            new BestEffortIntegrationObservationRecorder(
                persistence,
                classifier,
                log
            );

        assertDoesNotThrow(
            () -> recorder.record(
                successfulObservation()
            )
        );
    }

    @Test
    void shouldRejectInvalidDependenciesAndNullObservation() {

        IntegrationObservationPersistencePort persistence =
            observation ->
                copyWithId(
                    observation,
                    1L
                );

        ProcessingFailureClassifier classifier =
            failure ->
                new FailureClassification(
                    ProcessingFailureType.PERMANENT,
                    "TEST_FAILURE",
                    "test failure"
                );

        StructuredOperationalLogPort log =
            event -> {
            };

        assertThrows(
            NullPointerException.class,
            () ->
                new BestEffortIntegrationObservationRecorder(
                    null,
                    classifier,
                    log
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new BestEffortIntegrationObservationRecorder(
                    persistence,
                    null,
                    log
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new BestEffortIntegrationObservationRecorder(
                    persistence,
                    classifier,
                    null
                )
        );

        BestEffortIntegrationObservationRecorder recorder =
            new BestEffortIntegrationObservationRecorder(
                persistence,
                classifier,
                log
            );

        assertThrows(
            NullPointerException.class,
            () -> recorder.record(
                null
            )
        );
    }

    private IntegrationObservation successfulObservation() {

        return new IntegrationObservation(
            null,
            OBSERVED_AT,
            "amazon-deals-http",
            "GET",
            IntegrationObservationOutcome.SUCCESS,
            125L,
            context(),
            null,
            null,
            null,
            200
        );
    }

    private IntegrationObservation failedExternalObservation() {

        return new IntegrationObservation(
            null,
            OBSERVED_AT,
            "amazon-deals-http",
            "GET",
            IntegrationObservationOutcome.FAILURE,
            750L,
            context(),
            OperationalFailureOrigin.EXTERNAL,
            ProcessingFailureType.TRANSIENT,
            "COLLECTION_HTTP_503",
            503
        );
    }

    private OperationalLogContext context() {

        return new OperationalLogContext(
            101L,
            202L,
            ProcessingJobType.COLLECT_DEALS,
            null,
            null,
            null,
            null,
            "B0OBSERVE01",
            null
        );
    }

    private static IntegrationObservation copyWithId(
        IntegrationObservation observation,
        long id
    ) {

        return new IntegrationObservation(
            id,
            observation.observedAt(),
            observation.integration(),
            observation.operation(),
            observation.outcome(),
            observation.durationMs(),
            observation.context(),
            observation.failureOrigin(),
            observation.failureType(),
            observation.errorCode(),
            observation.httpStatusCode()
        );
    }

    private static final class RecordingPersistence
        implements IntegrationObservationPersistencePort {

        private final AtomicInteger calls =
            new AtomicInteger();

        private IntegrationObservation lastObservation;

        @Override
        public IntegrationObservation save(
            IntegrationObservation observation
        ) {

            calls.incrementAndGet();

            lastObservation =
                observation;

            return copyWithId(
                observation,
                1L
            );
        }
    }

    private static final class RecordingLog
        implements StructuredOperationalLogPort {

        private final List<OperationalLogEvent> events =
            new ArrayList<>();

        @Override
        public void log(
            OperationalLogEvent event
        ) {

            events.add(
                event
            );
        }
    }
}
