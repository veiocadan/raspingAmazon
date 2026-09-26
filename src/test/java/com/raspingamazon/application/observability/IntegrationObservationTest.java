package com.raspingamazon.application.observability;

import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntegrationObservationTest {

    private static final OffsetDateTime OBSERVED_AT =
        OffsetDateTime.parse(
            "2026-09-26T13:00:00Z"
        );

    @Test
    void shouldCreateSuccessfulObservation() {

        IntegrationObservation observation =
            new IntegrationObservation(
                null,
                OBSERVED_AT,
                "amazon-deals-http",
                "GET",
                IntegrationObservationOutcome.SUCCESS,
                125L,
                OperationalLogContext.empty(),
                null,
                null,
                null,
                200
            );

        assertNull(
            observation.id()
        );

        assertEquals(
            OBSERVED_AT,
            observation.observedAt()
        );

        assertEquals(
            "amazon-deals-http",
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
    }

    @Test
    void shouldCreateFailedExternalObservation() {

        OperationalLogContext context =
            new OperationalLogContext(
                101L,
                202L,
                ProcessingJobType.ENRICH_DEAL,
                303L,
                null,
                null,
                null,
                "B0ABC12345",
                "amazon-product-page"
            );

        IntegrationObservation observation =
            new IntegrationObservation(
                1L,
                OBSERVED_AT,
                "amazon-product-page",
                "GET",
                IntegrationObservationOutcome.FAILURE,
                750L,
                context,
                OperationalFailureOrigin.EXTERNAL,
                ProcessingFailureType.TRANSIENT,
                "NETWORK_TIMEOUT",
                null
            );

        assertEquals(
            1L,
            observation.id()
        );

        assertEquals(
            IntegrationObservationOutcome.FAILURE,
            observation.outcome()
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

        assertNull(
            observation.httpStatusCode()
        );

        assertEquals(
            "B0ABC12345",
            observation.context()
                .asin()
        );
    }

    @Test
    void shouldRejectInvalidPersistedId() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IntegrationObservation(
                    0L,
                    OBSERVED_AT,
                    "integration",
                    "GET",
                    IntegrationObservationOutcome.SUCCESS,
                    1L,
                    OperationalLogContext.empty(),
                    null,
                    null,
                    null,
                    200
                )
        );
    }

    @Test
    void shouldRejectMissingOrBlankIdentityFields() {

        assertThrows(
            NullPointerException.class,
            () ->
                new IntegrationObservation(
                    null,
                    OBSERVED_AT,
                    null,
                    "GET",
                    IntegrationObservationOutcome.SUCCESS,
                    1L,
                    OperationalLogContext.empty(),
                    null,
                    null,
                    null,
                    200
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IntegrationObservation(
                    null,
                    OBSERVED_AT,
                    " ",
                    "GET",
                    IntegrationObservationOutcome.SUCCESS,
                    1L,
                    OperationalLogContext.empty(),
                    null,
                    null,
                    null,
                    200
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IntegrationObservation(
                    null,
                    OBSERVED_AT,
                    "integration",
                    "",
                    IntegrationObservationOutcome.SUCCESS,
                    1L,
                    OperationalLogContext.empty(),
                    null,
                    null,
                    null,
                    200
                )
        );
    }

    @Test
    void shouldRejectNegativeDuration() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IntegrationObservation(
                    null,
                    OBSERVED_AT,
                    "integration",
                    "GET",
                    IntegrationObservationOutcome.SUCCESS,
                    -1L,
                    OperationalLogContext.empty(),
                    null,
                    null,
                    null,
                    200
                )
        );
    }

    @Test
    void shouldRejectInvalidHttpStatusCode() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IntegrationObservation(
                    null,
                    OBSERVED_AT,
                    "integration",
                    "GET",
                    IntegrationObservationOutcome.SUCCESS,
                    1L,
                    OperationalLogContext.empty(),
                    null,
                    null,
                    null,
                    99
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IntegrationObservation(
                    null,
                    OBSERVED_AT,
                    "integration",
                    "GET",
                    IntegrationObservationOutcome.SUCCESS,
                    1L,
                    OperationalLogContext.empty(),
                    null,
                    null,
                    null,
                    600
                )
        );
    }

    @Test
    void shouldRejectFailureMetadataOnSuccessfulObservation() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IntegrationObservation(
                    null,
                    OBSERVED_AT,
                    "integration",
                    "GET",
                    IntegrationObservationOutcome.SUCCESS,
                    1L,
                    OperationalLogContext.empty(),
                    OperationalFailureOrigin.EXTERNAL,
                    ProcessingFailureType.TRANSIENT,
                    "NETWORK_TIMEOUT",
                    null
                )
        );
    }

    @Test
    void shouldRequireCompleteFailureMetadata() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IntegrationObservation(
                    null,
                    OBSERVED_AT,
                    "integration",
                    "GET",
                    IntegrationObservationOutcome.FAILURE,
                    1L,
                    OperationalLogContext.empty(),
                    null,
                    ProcessingFailureType.TRANSIENT,
                    "NETWORK_TIMEOUT",
                    null
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IntegrationObservation(
                    null,
                    OBSERVED_AT,
                    "integration",
                    "GET",
                    IntegrationObservationOutcome.FAILURE,
                    1L,
                    OperationalLogContext.empty(),
                    OperationalFailureOrigin.EXTERNAL,
                    null,
                    "NETWORK_TIMEOUT",
                    null
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IntegrationObservation(
                    null,
                    OBSERVED_AT,
                    "integration",
                    "GET",
                    IntegrationObservationOutcome.FAILURE,
                    1L,
                    OperationalLogContext.empty(),
                    OperationalFailureOrigin.EXTERNAL,
                    ProcessingFailureType.TRANSIENT,
                    null,
                    null
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new IntegrationObservation(
                    null,
                    OBSERVED_AT,
                    "integration",
                    "GET",
                    IntegrationObservationOutcome.FAILURE,
                    1L,
                    OperationalLogContext.empty(),
                    OperationalFailureOrigin.EXTERNAL,
                    ProcessingFailureType.TRANSIENT,
                    " ",
                    null
                )
        );
    }
}
