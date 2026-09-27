package com.raspingamazon.application.operation.orchestration.run;

import com.raspingamazon.application.operation.orchestration.run.port.ProcessingRunOperationalDetailQueryPort;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetProcessingRunDetailUseCaseTest {

    private static final OffsetDateTime TIME =
        OffsetDateTime.parse(
            "2026-09-26T11:00:00-03:00"
        );

    @Test
    void shouldDelegateRunIdToQueryPort() {

        ProcessingRunDetail expected =
            detail();

        AtomicLong receivedId =
            new AtomicLong();

        ProcessingRunOperationalDetailQueryPort queryPort =
            runId -> {

                receivedId.set(
                    runId
                );

                return Optional.of(
                    expected
                );
            };

        GetProcessingRunDetailUseCase useCase =
            new GetProcessingRunDetailUseCase(
                queryPort
            );

        ProcessingRunDetail actual =
            useCase.execute(
                    10L
                )
                .orElseThrow();

        assertSame(
            expected,
            actual
        );

        assertEquals(
            10L,
            receivedId.get()
        );
    }

    @Test
    void shouldPreserveEmptyResult() {

        GetProcessingRunDetailUseCase useCase =
            new GetProcessingRunDetailUseCase(
                runId -> Optional.empty()
            );

        assertTrue(
            useCase.execute(
                    10L
                )
                .isEmpty()
        );
    }

    @Test
    void shouldRejectNonPositiveRunId() {

        GetProcessingRunDetailUseCase useCase =
            new GetProcessingRunDetailUseCase(
                runId -> Optional.empty()
            );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                0L
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.execute(
                -1L
            )
        );
    }

    @Test
    void shouldRejectNullQueryPort() {

        assertThrows(
            NullPointerException.class,
            () -> new GetProcessingRunDetailUseCase(
                null
            )
        );
    }

    @Test
    void shouldRejectNullQueryResult() {

        GetProcessingRunDetailUseCase useCase =
            new GetProcessingRunDetailUseCase(
                runId -> null
            );

        assertThrows(
            NullPointerException.class,
            () -> useCase.execute(
                10L
            )
        );
    }

    private ProcessingRunDetail detail() {

        ProcessingRunSummary summary =
            new ProcessingRunSummary(
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

        return new ProcessingRunDetail(
            summary,
            pipeline,
            jobs
        );
    }
}
