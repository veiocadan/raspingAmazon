package com.raspingamazon.application.publication;

import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingRunPublicationReadinessTest {

    @Test
    void completedRunWithoutCandidatesShouldBeReady() {

        ProcessingRunPublicationReadiness readiness =
            ProcessingRunPublicationReadiness.from(
                10L,
                ProcessingRunStatus.COMPLETED,
                0L,
                0L,
                0L,
                0L
            );

        assertEquals(
            ProcessingRunPublicationReadinessStatus.READY,
            readiness.status()
        );

        assertTrue(
            readiness.ready()
        );
    }

    @Test
    void completedRunWithAllCandidatesCompletedShouldBeReady() {

        ProcessingRunPublicationReadiness readiness =
            ProcessingRunPublicationReadiness.from(
                10L,
                ProcessingRunStatus.COMPLETED,
                3L,
                3L,
                0L,
                0L
            );

        assertTrue(
            readiness.ready()
        );

        assertFalse(
            readiness.inProgress()
        );

        assertFalse(
            readiness.blocked()
        );
    }

    @Test
    void pendingRunShouldRemainInProgress() {

        ProcessingRunPublicationReadiness readiness =
            ProcessingRunPublicationReadiness.from(
                10L,
                ProcessingRunStatus.PENDING,
                0L,
                0L,
                0L,
                0L
            );

        assertTrue(
            readiness.inProgress()
        );
    }

    @Test
    void runningRunShouldRemainInProgress() {

        ProcessingRunPublicationReadiness readiness =
            ProcessingRunPublicationReadiness.from(
                10L,
                ProcessingRunStatus.RUNNING,
                2L,
                0L,
                2L,
                0L
            );

        assertTrue(
            readiness.inProgress()
        );
    }

    @Test
    void failedRunShouldBeBlocked() {

        ProcessingRunPublicationReadiness readiness =
            ProcessingRunPublicationReadiness.from(
                10L,
                ProcessingRunStatus.FAILED,
                0L,
                0L,
                0L,
                0L
            );

        assertTrue(
            readiness.blocked()
        );
    }

    @Test
    void completedRunWithCandidateStillProcessingShouldRemainInProgress() {

        ProcessingRunPublicationReadiness readiness =
            ProcessingRunPublicationReadiness.from(
                10L,
                ProcessingRunStatus.COMPLETED,
                4L,
                3L,
                1L,
                0L
            );

        assertTrue(
            readiness.inProgress()
        );
    }

    @Test
    void blockedCandidateShouldBlockWholeCompletedRun() {

        ProcessingRunPublicationReadiness readiness =
            ProcessingRunPublicationReadiness.from(
                10L,
                ProcessingRunStatus.COMPLETED,
                4L,
                2L,
                1L,
                1L
            );

        assertTrue(
            readiness.blocked()
        );
    }

    @Test
    void shouldRejectInconsistentCandidateCounts() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                ProcessingRunPublicationReadiness.from(
                    10L,
                    ProcessingRunStatus.COMPLETED,
                    3L,
                    2L,
                    0L,
                    0L
                )
        );
    }
}
