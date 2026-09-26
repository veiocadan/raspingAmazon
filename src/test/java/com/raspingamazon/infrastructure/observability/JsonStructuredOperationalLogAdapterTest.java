package com.raspingamazon.infrastructure.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.observability.OperationalLogContext;
import com.raspingamazon.application.observability.OperationalLogEvent;
import com.raspingamazon.application.observability.OperationalLogLevel;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonStructuredOperationalLogAdapterTest {

    private static final Instant FIXED_INSTANT =
        Instant.parse(
            "2026-09-26T12:30:00Z"
        );

    @Test
    void shouldWriteCompleteStructuredEventAsSingleJsonLine()
        throws Exception {

        TestOutput output =
            new TestOutput();

        JsonStructuredOperationalLogAdapter adapter =
            adapter(
                output
            );

        adapter.log(
            completeEvent()
        );

        List<String> lines =
            output.text()
                .lines()
                .toList();

        assertEquals(
            1,
            lines.size()
        );

        JsonNode json =
            new ObjectMapper()
                .readTree(
                    lines.getFirst()
                );

        assertEquals(
            "2026-09-26T12:30Z",
            json.get(
                "timestamp"
            ).asText()
        );

        assertEquals(
            "WARN",
            json.get(
                "level"
            ).asText()
        );

        assertEquals(
            "processing.job.retry-scheduled",
            json.get(
                "event"
            ).asText()
        );

        assertEquals(
            "processing-worker",
            json.get(
                "component"
            ).asText()
        );

        assertEquals(
            "execute-job",
            json.get(
                "operation"
            ).asText()
        );

        assertEquals(
            101L,
            json.get(
                "runId"
            ).asLong()
        );

        assertEquals(
            202L,
            json.get(
                "jobId"
            ).asLong()
        );

        assertEquals(
            "EVALUATE_DEAL",
            json.get(
                "jobType"
            ).asText()
        );

        assertEquals(
            303L,
            json.get(
                "candidateId"
            ).asLong()
        );

        assertEquals(
            404L,
            json.get(
                "snapshotId"
            ).asLong()
        );

        assertEquals(
            505L,
            json.get(
                "evaluationId"
            ).asLong()
        );

        assertEquals(
            606L,
            json.get(
                "publicationId"
            ).asLong()
        );

        assertEquals(
            "B0ABC12345",
            json.get(
                "asin"
            ).asText()
        );

        assertEquals(
            "amazon-product-page",
            json.get(
                "integration"
            ).asText()
        );

        assertEquals(
            "RETRY_SCHEDULED",
            json.get(
                "outcome"
            ).asText()
        );

        assertEquals(
            250L,
            json.get(
                "durationMs"
            ).asLong()
        );

        assertEquals(
            "EXTERNAL",
            json.get(
                "failureOrigin"
            ).asText()
        );

        assertEquals(
            "TRANSIENT",
            json.get(
                "failureType"
            ).asText()
        );

        assertEquals(
            "HTTP_503",
            json.get(
                "errorCode"
            ).asText()
        );
    }

    @Test
    void shouldOmitAbsentOptionalFields()
        throws Exception {

        TestOutput output =
            new TestOutput();

        JsonStructuredOperationalLogAdapter adapter =
            adapter(
                output
            );

        adapter.log(
            new OperationalLogEvent(
                OperationalLogLevel.INFO,
                "processing.worker.idle",
                "processing-worker",
                "claim-job",
                OperationalLogContext.empty(),
                "NO_WORK",
                null,
                null,
                null,
                null
            )
        );

        JsonNode json =
            new ObjectMapper()
                .readTree(
                    output.text()
                );

        assertTrue(
            json.has(
                "timestamp"
            )
        );

        assertTrue(
            json.has(
                "level"
            )
        );

        assertTrue(
            json.has(
                "event"
            )
        );

        assertTrue(
            json.has(
                "component"
            )
        );

        assertTrue(
            json.has(
                "operation"
            )
        );

        assertTrue(
            json.has(
                "outcome"
            )
        );

        assertFalse(
            json.has(
                "runId"
            )
        );

        assertFalse(
            json.has(
                "jobId"
            )
        );

        assertFalse(
            json.has(
                "jobType"
            )
        );

        assertFalse(
            json.has(
                "candidateId"
            )
        );

        assertFalse(
            json.has(
                "snapshotId"
            )
        );

        assertFalse(
            json.has(
                "evaluationId"
            )
        );

        assertFalse(
            json.has(
                "publicationId"
            )
        );

        assertFalse(
            json.has(
                "asin"
            )
        );

        assertFalse(
            json.has(
                "integration"
            )
        );

        assertFalse(
            json.has(
                "durationMs"
            )
        );

        assertFalse(
            json.has(
                "failureOrigin"
            )
        );

        assertFalse(
            json.has(
                "failureType"
            )
        );

        assertFalse(
            json.has(
                "errorCode"
            )
        );
    }

    @Test
    void shouldEscapeStructuredTextWithoutBreakingJsonLines()
        throws Exception {

        TestOutput output =
            new TestOutput();

        JsonStructuredOperationalLogAdapter adapter =
            adapter(
                output
            );

        String eventName =
            "event.\"quoted\"\nvalue";

        adapter.log(
            new OperationalLogEvent(
                OperationalLogLevel.INFO,
                eventName,
                "component",
                "operation",
                OperationalLogContext.empty(),
                "SUCCESS",
                0L,
                null,
                null,
                null
            )
        );

        List<String> lines =
            output.text()
                .lines()
                .toList();

        assertEquals(
            1,
            lines.size()
        );

        JsonNode json =
            new ObjectMapper()
                .readTree(
                    lines.getFirst()
                );

        assertEquals(
            eventName,
            json.get(
                "event"
            ).asText()
        );
    }

    @Test
    void shouldRejectNullDependencies() {

        PrintWriter writer =
            new PrintWriter(
                new ByteArrayOutputStream(),
                true,
                StandardCharsets.UTF_8
            );

        Clock clock =
            Clock.fixed(
                FIXED_INSTANT,
                ZoneOffset.UTC
            );

        assertThrows(
            NullPointerException.class,
            () ->
                new JsonStructuredOperationalLogAdapter(
                    null,
                    clock
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new JsonStructuredOperationalLogAdapter(
                    writer,
                    null
                )
        );
    }

    @Test
    void shouldRejectNullEvent() {

        TestOutput output =
            new TestOutput();

        JsonStructuredOperationalLogAdapter adapter =
            adapter(
                output
            );

        assertThrows(
            NullPointerException.class,
            () -> adapter.log(
                null
            )
        );
    }

    private JsonStructuredOperationalLogAdapter adapter(
        TestOutput output
    ) {

        return new JsonStructuredOperationalLogAdapter(
            output.writer(),
            Clock.fixed(
                FIXED_INSTANT,
                ZoneOffset.UTC
            )
        );
    }

    private OperationalLogEvent completeEvent() {

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

        return new OperationalLogEvent(
            OperationalLogLevel.WARN,
            "processing.job.retry-scheduled",
            "processing-worker",
            "execute-job",
            context,
            "RETRY_SCHEDULED",
            250L,
            OperationalFailureOrigin.EXTERNAL,
            ProcessingFailureType.TRANSIENT,
            "HTTP_503"
        );
    }

    private static final class TestOutput {

        private final ByteArrayOutputStream buffer =
            new ByteArrayOutputStream();

        private final PrintWriter writer =
            new PrintWriter(
                buffer,
                true,
                StandardCharsets.UTF_8
            );

        PrintWriter writer() {

            return writer;
        }

        String text() {

            writer.flush();

            return buffer.toString(
                StandardCharsets.UTF_8
            );
        }
    }
}
