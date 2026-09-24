package com.raspingamazon.application.operation.orchestration.run;

import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingRunOperationalContractsTest {

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

        ProcessingRunCursor cursor =
            new ProcessingRunCursor(
                SECOND_TIME,
                20L
            );

        assertEquals(
            SECOND_TIME,
            cursor.requestedAt()
        );

        assertEquals(
            20L,
            cursor.runId()
        );
    }

    @Test
    void shouldRejectInvalidCursorId() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingRunCursor(
                SECOND_TIME,
                0L
            )
        );
    }

    @Test
    void shouldCreateDefaultSearchCriteria() {

        ProcessingRunSearchCriteria criteria =
            ProcessingRunSearchCriteria.firstPage();

        assertNull(
            criteria.status()
        );

        assertNull(
            criteria.after()
        );

        assertEquals(
            ProcessingRunSearchCriteria.DEFAULT_LIMIT,
            criteria.limit()
        );
    }

    @Test
    void shouldRejectInvalidLimit() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingRunSearchCriteria(
                null,
                null,
                null,
                null,
                0
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingRunSearchCriteria(
                null,
                null,
                null,
                null,
                ProcessingRunSearchCriteria.MAX_LIMIT + 1
            )
        );
    }

    @Test
    void shouldRejectInvertedRequestedRange() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingRunSearchCriteria(
                null,
                SECOND_TIME,
                FIRST_TIME,
                null,
                50
            )
        );
    }

    @Test
    void shouldExposeFailedRun() {

        ProcessingRunSummary summary =
            summary(
                20L,
                SECOND_TIME,
                ProcessingRunStatus.FAILED,
                "HTTP_500",
                "Temporary upstream failure"
            );

        assertTrue(
            summary.failed()
        );

        assertEquals(
            "HTTP_500",
            summary.lastErrorCode()
        );
    }

    @Test
    void shouldCreateOrderedPageWithNextCursor() {

        ProcessingRunSummary newest =
            summary(
                20L,
                SECOND_TIME,
                ProcessingRunStatus.COMPLETED,
                null,
                null
            );

        ProcessingRunSummary oldest =
            summary(
                10L,
                FIRST_TIME,
                ProcessingRunStatus.COMPLETED,
                null,
                null
            );

        ProcessingRunCursor cursor =
            new ProcessingRunCursor(
                oldest.requestedAt(),
                oldest.runId()
            );

        ProcessingRunPage page =
            new ProcessingRunPage(
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

        ProcessingRunSummary older =
            summary(
                10L,
                FIRST_TIME,
                ProcessingRunStatus.COMPLETED,
                null,
                null
            );

        ProcessingRunSummary newer =
            summary(
                20L,
                SECOND_TIME,
                ProcessingRunStatus.COMPLETED,
                null,
                null
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> new ProcessingRunPage(
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

        ArrayList<ProcessingRunSummary> mutable =
            new ArrayList<>();

        mutable.add(
            summary(
                20L,
                SECOND_TIME,
                ProcessingRunStatus.PENDING,
                null,
                null
            )
        );

        ProcessingRunPage page =
            new ProcessingRunPage(
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

    private ProcessingRunSummary summary(
        long id,
        OffsetDateTime requestedAt,
        ProcessingRunStatus status,
        String errorCode,
        String errorMessage
    ) {

        OffsetDateTime startedAt =
            status == ProcessingRunStatus.PENDING
                ? null
                : requestedAt.plusMinutes(
                1
            );

        OffsetDateTime completedAt =
            status == ProcessingRunStatus.COMPLETED
                || status == ProcessingRunStatus.FAILED
                ? requestedAt.plusMinutes(
                2
            )
                : null;

        return new ProcessingRunSummary(
            id,
            "run-" + id,
            "https://www.amazon.com.br/deals",
            status,
            requestedAt,
            startedAt,
            completedAt,
            errorCode,
            errorMessage,
            requestedAt,
            requestedAt.plusMinutes(
                2
            )
        );
    }
}
