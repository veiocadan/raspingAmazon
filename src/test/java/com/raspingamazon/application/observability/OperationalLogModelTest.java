package com.raspingamazon.application.observability;

import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationalLogModelTest {

    @Test
    void shouldCreateContextWithAllCorrelationFields() {

        OperationalLogContext context =
            new OperationalLogContext(
                101L,
                202L,
                ProcessingJobType.EVALUATE_DEAL,
                303L,
                404L,
                505L,
                606L,
                "B0ABC12345",
                "amazon-product-page"
            );

        assertEquals(
            101L,
            context.runId()
        );

        assertEquals(
            202L,
            context.jobId()
        );

        assertEquals(
            ProcessingJobType.EVALUATE_DEAL,
            context.jobType()
        );

        assertEquals(
            303L,
            context.candidateId()
        );

        assertEquals(
            404L,
            context.snapshotId()
        );

        assertEquals(
            505L,
            context.evaluationId()
        );

        assertEquals(
            606L,
            context.publicationId()
        );

        assertEquals(
            "B0ABC12345",
            context.asin()
        );

        assertEquals(
            "amazon-product-page",
            context.integration()
        );
    }

    @Test
    void shouldCreateEmptyContext() {

        OperationalLogContext context =
            OperationalLogContext.empty();

        assertNull(
            context.runId()
        );

        assertNull(
            context.jobId()
        );

        assertNull(
            context.jobType()
        );

        assertNull(
            context.candidateId()
        );

        assertNull(
            context.snapshotId()
        );

        assertNull(
            context.evaluationId()
        );

        assertNull(
            context.publicationId()
        );

        assertNull(
            context.asin()
        );

        assertNull(
            context.integration()
        );
    }

    @Test
    void shouldRejectInvalidCorrelationIds() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogContext(
                0L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogContext(
                null,
                -1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogContext(
                null,
                null,
                null,
                0L,
                null,
                null,
                null,
                null,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogContext(
                null,
                null,
                null,
                null,
                -1L,
                null,
                null,
                null,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogContext(
                null,
                null,
                null,
                null,
                null,
                0L,
                null,
                null,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogContext(
                null,
                null,
                null,
                null,
                null,
                null,
                -1L,
                null,
                null
            )
        );
    }

    @Test
    void shouldRejectBlankOptionalContextText() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogContext(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                " ",
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogContext(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ""
            )
        );
    }

    @Test
    void shouldCreateStructuredEventWithoutFailure() {

        OperationalLogEvent event =
            new OperationalLogEvent(
                OperationalLogLevel.INFO,
                "processing.job.succeeded",
                "processing-worker",
                "execute-job",
                OperationalLogContext.empty(),
                "SUCCEEDED",
                125L,
                null,
                null,
                null
            );

        assertEquals(
            OperationalLogLevel.INFO,
            event.level()
        );

        assertEquals(
            "processing.job.succeeded",
            event.event()
        );

        assertEquals(
            "processing-worker",
            event.component()
        );

        assertEquals(
            "execute-job",
            event.operation()
        );

        assertEquals(
            "SUCCEEDED",
            event.outcome()
        );

        assertEquals(
            125L,
            event.durationMs()
        );

        assertNull(
            event.failureOrigin()
        );

        assertNull(
            event.failureType()
        );

        assertNull(
            event.errorCode()
        );
    }

    @Test
    void shouldCreateStructuredFailureEvent() {

        OperationalLogEvent event =
            new OperationalLogEvent(
                OperationalLogLevel.WARN,
                "processing.job.retry-scheduled",
                "processing-worker",
                "execute-job",
                OperationalLogContext.empty(),
                "RETRY_SCHEDULED",
                250L,
                OperationalFailureOrigin.EXTERNAL,
                ProcessingFailureType.TRANSIENT,
                "HTTP_503"
            );

        assertEquals(
            OperationalFailureOrigin.EXTERNAL,
            event.failureOrigin()
        );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            event.failureType()
        );

        assertEquals(
            "HTTP_503",
            event.errorCode()
        );
    }

    @Test
    void shouldRejectMissingRequiredEventFields() {

        assertThrows(
            NullPointerException.class,
            () -> new OperationalLogEvent(
                null,
                "event",
                "component",
                "operation",
                OperationalLogContext.empty(),
                "SUCCESS",
                null,
                null,
                null,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogEvent(
                OperationalLogLevel.INFO,
                " ",
                "component",
                "operation",
                OperationalLogContext.empty(),
                "SUCCESS",
                null,
                null,
                null,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogEvent(
                OperationalLogLevel.INFO,
                "event",
                "",
                "operation",
                OperationalLogContext.empty(),
                "SUCCESS",
                null,
                null,
                null,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogEvent(
                OperationalLogLevel.INFO,
                "event",
                "component",
                " ",
                OperationalLogContext.empty(),
                "SUCCESS",
                null,
                null,
                null,
                null
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> new OperationalLogEvent(
                OperationalLogLevel.INFO,
                "event",
                "component",
                "operation",
                null,
                "SUCCESS",
                null,
                null,
                null,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogEvent(
                OperationalLogLevel.INFO,
                "event",
                "component",
                "operation",
                OperationalLogContext.empty(),
                "",
                null,
                null,
                null,
                null
            )
        );
    }

    @Test
    void shouldRejectNegativeDuration() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogEvent(
                OperationalLogLevel.INFO,
                "event",
                "component",
                "operation",
                OperationalLogContext.empty(),
                "SUCCESS",
                -1L,
                null,
                null,
                null
            )
        );
    }

    @Test
    void shouldRequireCompleteFailureMetadata() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogEvent(
                OperationalLogLevel.ERROR,
                "event",
                "component",
                "operation",
                OperationalLogContext.empty(),
                "FAILED",
                null,
                OperationalFailureOrigin.INTERNAL,
                null,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogEvent(
                OperationalLogLevel.ERROR,
                "event",
                "component",
                "operation",
                OperationalLogContext.empty(),
                "FAILED",
                null,
                null,
                ProcessingFailureType.PERMANENT,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogEvent(
                OperationalLogLevel.ERROR,
                "event",
                "component",
                "operation",
                OperationalLogContext.empty(),
                "FAILED",
                null,
                OperationalFailureOrigin.INTERNAL,
                ProcessingFailureType.PERMANENT,
                null
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new OperationalLogEvent(
                OperationalLogLevel.ERROR,
                "event",
                "component",
                "operation",
                OperationalLogContext.empty(),
                "FAILED",
                null,
                OperationalFailureOrigin.INTERNAL,
                ProcessingFailureType.PERMANENT,
                " "
            )
        );
    }
}
