package com.raspingamazon.application.operation.orchestration.run;

import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingRunDetailTest {

    private static final OffsetDateTime TIME =
        OffsetDateTime.parse(
            "2026-09-26T11:00:00-03:00"
        );

    @Test
    void shouldRepresentPipelineMetrics() {

        ProcessingRunPipelineMetrics metrics =
            new ProcessingRunPipelineMetrics(
                10L,
                8L,
                7L,
                5L,
                2L,
                3L
            );

        assertEquals(
            10L,
            metrics.collectedCandidates()
        );

        assertEquals(
            8L,
            metrics.enrichedCandidates()
        );

        assertEquals(
            2L,
            metrics.pendingEnrichmentCandidates()
        );

        assertEquals(
            7L,
            metrics.evaluations()
        );

        assertEquals(
            5L,
            metrics.eligibleEvaluations()
        );

        assertEquals(
            2L,
            metrics.rejectedEvaluations()
        );

        assertEquals(
            3L,
            metrics.publicationsGenerated()
        );

        assertTrue(
            metrics.hasRejectedEvaluations()
        );
    }

    @Test
    void shouldRejectNegativePipelineMetrics() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingRunPipelineMetrics(
                -1L,
                0L,
                0L,
                0L,
                0L,
                0L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingRunPipelineMetrics(
                1L,
                0L,
                0L,
                0L,
                0L,
                -1L
            )
        );
    }

    @Test
    void shouldRejectMoreEnrichedCandidatesThanCollectedCandidates() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingRunPipelineMetrics(
                2L,
                3L,
                0L,
                0L,
                0L,
                0L
            )
        );
    }

    @Test
    void shouldRejectInconsistentEvaluationBreakdown() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingRunPipelineMetrics(
                10L,
                10L,
                8L,
                5L,
                2L,
                0L
            )
        );
    }

    @Test
    void shouldRepresentJobMetrics() {

        ProcessingRunJobMetrics metrics =
            new ProcessingRunJobMetrics(
                10L,
                1L,
                1L,
                2L,
                5L,
                1L,
                14L,
                4L
            );

        assertEquals(
            10L,
            metrics.totalJobs()
        );

        assertEquals(
            14L,
            metrics.totalAttempts()
        );

        assertEquals(
            4L,
            metrics.retryAttempts()
        );

        assertTrue(
            metrics.hasDeadJobs()
        );

        assertTrue(
            metrics.hasActiveJobs()
        );
    }

    @Test
    void shouldRejectInconsistentJobStatusBreakdown() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingRunJobMetrics(
                10L,
                1L,
                1L,
                1L,
                5L,
                1L,
                9L,
                0L
            )
        );
    }

    @Test
    void shouldRejectMoreRetriesThanAttempts() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingRunJobMetrics(
                1L,
                0L,
                0L,
                0L,
                1L,
                0L,
                1L,
                2L
            )
        );
    }

    @Test
    void shouldRepresentRunDetail() {

        ProcessingRunSummary summary =
            summary();

        ProcessingRunPipelineMetrics pipeline =
            new ProcessingRunPipelineMetrics(
                4L,
                4L,
                4L,
                3L,
                1L,
                2L
            );

        ProcessingRunJobMetrics jobs =
            new ProcessingRunJobMetrics(
                9L,
                0L,
                0L,
                0L,
                9L,
                0L,
                9L,
                0L
            );

        ProcessingRunDetail detail =
            new ProcessingRunDetail(
                summary,
                pipeline,
                jobs
            );

        assertSame(
            summary,
            detail.summary()
        );

        assertSame(
            pipeline,
            detail.pipeline()
        );

        assertSame(
            jobs,
            detail.jobs()
        );

        assertFalse(
            jobs.hasActiveJobs()
        );
    }

    @Test
    void shouldRejectNullRunDetailComponents() {

        ProcessingRunSummary summary =
            summary();

        ProcessingRunPipelineMetrics pipeline =
            new ProcessingRunPipelineMetrics(
                0L,
                0L,
                0L,
                0L,
                0L,
                0L
            );

        ProcessingRunJobMetrics jobs =
            new ProcessingRunJobMetrics(
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L
            );

        assertThrows(
            NullPointerException.class,
            () -> new ProcessingRunDetail(
                null,
                pipeline,
                jobs
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> new ProcessingRunDetail(
                summary,
                null,
                jobs
            )
        );

        assertThrows(
            NullPointerException.class,
            () -> new ProcessingRunDetail(
                summary,
                pipeline,
                null
            )
        );
    }

    private ProcessingRunSummary summary() {

        return new ProcessingRunSummary(
            10L,
            "run-observability-10",
            "https://www.amazon.com.br/deals",
            ProcessingRunStatus.COMPLETED,
            TIME.minusMinutes(
                10
            ),
            TIME.minusMinutes(
                9
            ),
            TIME,
            null,
            null,
            TIME.minusMinutes(
                10
            ),
            TIME
        );
    }
}
