package com.raspingamazon.application.orchestration.reprocess;

import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingJobReprocessContractTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-04T02:30:00Z"
        );

    @Test
    void shouldNormalizeOperationalRequestText() {

        ProcessingJobReprocessRequest request =
            new ProcessingJobReprocessRequest(
                10L,
                "  reprocess-10-001  ",
                "  operator@example  ",
                "  provider configuration corrected  "
            );

        assertEquals(
            10L,
            request.processingJobId()
        );

        assertEquals(
            "reprocess-10-001",
            request.requestKey()
        );

        assertEquals(
            "operator@example",
            request.requestedBy()
        );

        assertEquals(
            "provider configuration corrected",
            request.reason()
        );
    }

    @Test
    void shouldRejectInvalidOperationalRequest() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ProcessingJobReprocessRequest(
                    0L,
                    "request",
                    "operator",
                    "reason"
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ProcessingJobReprocessRequest(
                    10L,
                    " ",
                    "operator",
                    "reason"
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ProcessingJobReprocessRequest(
                    10L,
                    "request",
                    "",
                    "reason"
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ProcessingJobReprocessRequest(
                    10L,
                    "request",
                    "operator",
                    " "
                )
        );
    }

    @Test
    void shouldRepresentNewlyAppliedReprocessing() {

        ProcessingJob job =
            deadJob();

        ProcessingJobReprocessResult result =
            new ProcessingJobReprocessResult(
                90L,
                "request-90",
                job,
                true
            );

        assertEquals(
            90L,
            result.reprocessEventId()
        );

        assertEquals(
            "request-90",
            result.requestKey()
        );

        assertEquals(
            job,
            result.job()
        );

        assertTrue(
            result.newlyApplied()
        );
    }

    @Test
    void shouldRepresentIdempotentReplay() {

        ProcessingJobReprocessResult result =
            new ProcessingJobReprocessResult(
                91L,
                "request-91",
                deadJob(),
                false
            );

        assertFalse(
            result.newlyApplied()
        );
    }

    @Test
    void shouldRejectInvalidResult() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ProcessingJobReprocessResult(
                    0L,
                    "request",
                    deadJob(),
                    true
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ProcessingJobReprocessResult(
                    1L,
                    " ",
                    deadJob(),
                    true
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new ProcessingJobReprocessResult(
                    1L,
                    "request",
                    null,
                    true
                )
        );
    }

    private ProcessingJob deadJob() {

        return new ProcessingJob(
            10L,
            ProcessingJobType.PUBLICATION_DISPATCH,
            ProcessingJobStatus.DEAD,
            20L,
            null,
            null,
            "publication-dispatch:20",
            3,
            3,
            NOW.minusMinutes(
                10
            ),
            null,
            null,
            ProcessingFailureType.PERMANENT,
            "PUBLICATION_BLOCKED",
            "Publication run is blocked",
            NOW.minusHours(
                1
            ),
            NOW,
            NOW
        );
    }
}
