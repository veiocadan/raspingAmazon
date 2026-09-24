package com.raspingamazon.application.operation.orchestration.job;

import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingJobOperationalContractsTest {

    private static final OffsetDateTime FIRST_TIME =
        OffsetDateTime.parse(
            "2026-09-24T10:00:00-03:00"
        );

    private static final OffsetDateTime SECOND_TIME =
        OffsetDateTime.parse(
            "2026-09-24T11:00:00-03:00"
        );

    @Test
    void shouldCreateValidCursor() {

        ProcessingJobCursor cursor =
            new ProcessingJobCursor(
                SECOND_TIME,
                20L
            );

        assertEquals(
            SECOND_TIME,
            cursor.createdAt()
        );

        assertEquals(
            20L,
            cursor.jobId()
        );
    }

    @Test
    void shouldRejectInvalidCursorId() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingJobCursor(
                SECOND_TIME,
                0L
            )
        );
    }

    @Test
    void shouldCreateDefaultSearchCriteria() {

        ProcessingJobSearchCriteria criteria =
            ProcessingJobSearchCriteria.firstPage();

        assertNull(
            criteria.type()
        );

        assertNull(
            criteria.status()
        );

        assertNull(
            criteria.after()
        );

        assertEquals(
            ProcessingJobSearchCriteria.DEFAULT_LIMIT,
            criteria.limit()
        );
    }

    @Test
    void shouldRejectInvalidLimits() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingJobSearchCriteria(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingJobSearchCriteria(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ProcessingJobSearchCriteria.MAX_LIMIT + 1
            )
        );
    }

    @Test
    void shouldRejectInvalidSubjectIdentifiers() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingJobSearchCriteria(
                null,
                null,
                null,
                0L,
                null,
                null,
                null,
                null,
                null,
                50
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingJobSearchCriteria(
                null,
                null,
                null,
                null,
                -1L,
                null,
                null,
                null,
                null,
                50
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingJobSearchCriteria(
                null,
                null,
                null,
                null,
                null,
                0L,
                null,
                null,
                null,
                50
            )
        );
    }

    @Test
    void shouldRejectInvertedCreationRange() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingJobSearchCriteria(
                null,
                null,
                null,
                null,
                null,
                null,
                SECOND_TIME,
                FIRST_TIME,
                null,
                50
            )
        );
    }

    @Test
    void shouldExposeRemainingAttemptsWithoutInferringRetryPermission() {

        ProcessingJobSummary summary =
            summary(
                20L,
                SECOND_TIME,
                ProcessingJobStatus.DEAD,
                2,
                5
            );

        assertEquals(
            3,
            summary.remainingAttempts()
        );

        assertTrue(
            summary.terminal()
        );
    }

    @Test
    void shouldCreateOrderedPage() {

        ProcessingJobSummary newest =
            summary(
                20L,
                SECOND_TIME,
                ProcessingJobStatus.PENDING,
                0,
                5
            );

        ProcessingJobSummary oldest =
            summary(
                10L,
                FIRST_TIME,
                ProcessingJobStatus.PENDING,
                0,
                5
            );

        ProcessingJobCursor cursor =
            new ProcessingJobCursor(
                oldest.createdAt(),
                oldest.jobId()
            );

        ProcessingJobPage page =
            new ProcessingJobPage(
                List.of(
                    newest,
                    oldest
                ),
                cursor
            );

        assertEquals(
            2,
            page.items()
                .size()
        );

        assertTrue(
            page.hasNextPage()
        );

        assertEquals(
            cursor,
            page.nextCursor()
        );
    }

    @Test
    void shouldRejectUnorderedPage() {

        ProcessingJobSummary older =
            summary(
                10L,
                FIRST_TIME,
                ProcessingJobStatus.PENDING,
                0,
                5
            );

        ProcessingJobSummary newer =
            summary(
                20L,
                SECOND_TIME,
                ProcessingJobStatus.PENDING,
                0,
                5
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingJobPage(
                List.of(
                    older,
                    newer
                ),
                null
            )
        );
    }

    @Test
    void shouldDefensivelyCopyPageItems() {

        ArrayList<ProcessingJobSummary> mutable =
            new ArrayList<>();

        mutable.add(
            summary(
                20L,
                SECOND_TIME,
                ProcessingJobStatus.PENDING,
                0,
                5
            )
        );

        ProcessingJobPage page =
            new ProcessingJobPage(
                mutable,
                null
            );

        mutable.clear();

        assertEquals(
            1,
            page.items()
                .size()
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> page.items()
                .clear()
        );

        assertFalse(
            page.hasNextPage()
        );
    }

    private ProcessingJobSummary summary(
        long id,
        OffsetDateTime createdAt,
        ProcessingJobStatus status,
        int attemptCount,
        int maxAttempts
    ) {

        OffsetDateTime finishedAt =
            status == ProcessingJobStatus.SUCCEEDED
                || status == ProcessingJobStatus.DEAD
                ? createdAt.plusMinutes(
                5
            )
                : null;

        ProcessingFailureType failureType =
            status == ProcessingJobStatus.DEAD
                ? ProcessingFailureType.PERMANENT
                : null;

        return new ProcessingJobSummary(
            id,
            ProcessingJobType.COLLECT_DEALS,
            status,
            100L,
            null,
            null,
            "job-" + id,
            attemptCount,
            maxAttempts,
            createdAt,
            null,
            null,
            failureType,
            failureType == null
                ? null
                : "TEST_FAILURE",
            failureType == null
                ? null
                : "Test failure",
            createdAt,
            createdAt.plusMinutes(
                1
            ),
            finishedAt
        );
    }
}
