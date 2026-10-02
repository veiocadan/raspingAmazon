package com.raspingamazon.application.publication;

import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationDispatchJobServiceTest {

    @Test
    void readyRunShouldExecuteAutomaticDispatch() {

        AtomicLong processedRunId =
            new AtomicLong();

        PublicationSelectionDispatchResult expected =
            new PublicationSelectionDispatchResult(
                700L,
                List.of()
            );

        PublicationDispatchJobService service =
            new PublicationDispatchJobService(
                processingRunId ->
                    Optional.of(
                        ready(
                            processingRunId
                        )
                    ),
                processingRunId -> {

                    processedRunId.set(
                        processingRunId
                    );

                    return expected;
                }
            );

        PublicationSelectionDispatchResult actual =
            service.execute(
                101L
            );

        assertSame(
            expected,
            actual
        );

        assertEquals(
            101L,
            processedRunId.get()
        );
    }

    @Test
    void blockedRunShouldFailWithoutDispatching() {

        AtomicInteger dispatchCalls =
            new AtomicInteger();

        PublicationDispatchJobService service =
            new PublicationDispatchJobService(
                processingRunId ->
                    Optional.of(
                        blocked(
                            processingRunId
                        )
                    ),
                processingRunId -> {

                    dispatchCalls.incrementAndGet();

                    throw new AssertionError(
                        "dispatch must not execute for blocked run"
                    );
                }
            );

        PublicationDispatchJobException exception =
            assertThrows(
                PublicationDispatchJobException.class,
                () ->
                    service.execute(
                        201L
                    )
            );

        assertEquals(
            PublicationDispatchJobException.Reason
                .PROCESSING_RUN_BLOCKED,
            exception.reason()
        );

        assertEquals(
            "PUBLICATION_DISPATCH_RUN_BLOCKED",
            exception.errorCode()
        );

        assertEquals(
            201L,
            exception.processingRunId()
        );

        assertEquals(
            0,
            dispatchCalls.get()
        );
    }

    @Test
    void inProgressRunShouldBeReportedAsInvariantViolation() {

        AtomicInteger dispatchCalls =
            new AtomicInteger();

        PublicationDispatchJobService service =
            new PublicationDispatchJobService(
                processingRunId ->
                    Optional.of(
                        inProgress(
                            processingRunId
                        )
                    ),
                processingRunId -> {

                    dispatchCalls.incrementAndGet();

                    throw new AssertionError(
                        "dispatch must not execute while "
                            + "ProcessingRun is IN_PROGRESS"
                    );
                }
            );

        PublicationDispatchJobException exception =
            assertThrows(
                PublicationDispatchJobException.class,
                () ->
                    service.execute(
                        301L
                    )
            );

        assertEquals(
            PublicationDispatchJobException.Reason
                .NOT_READY_INVARIANT,
            exception.reason()
        );

        assertEquals(
            "PUBLICATION_DISPATCH_NOT_READY_INVARIANT",
            exception.errorCode()
        );

        assertEquals(
            0,
            dispatchCalls.get()
        );
    }

    @Test
    void missingRunShouldFailPermanentlyBeforeDispatch() {

        AtomicInteger dispatchCalls =
            new AtomicInteger();

        PublicationDispatchJobService service =
            new PublicationDispatchJobService(
                processingRunId ->
                    Optional.empty(),
                processingRunId -> {

                    dispatchCalls.incrementAndGet();

                    throw new AssertionError(
                        "dispatch must not execute for missing run"
                    );
                }
            );

        PublicationDispatchJobException exception =
            assertThrows(
                PublicationDispatchJobException.class,
                () ->
                    service.execute(
                        401L
                    )
            );

        assertEquals(
            PublicationDispatchJobException.Reason
                .PROCESSING_RUN_NOT_FOUND,
            exception.reason()
        );

        assertEquals(
            "PUBLICATION_DISPATCH_RUN_NOT_FOUND",
            exception.errorCode()
        );

        assertEquals(
            0,
            dispatchCalls.get()
        );
    }

    @Test
    void shouldRejectReadinessForDifferentProcessingRun() {

        AtomicInteger dispatchCalls =
            new AtomicInteger();

        PublicationDispatchJobService service =
            new PublicationDispatchJobService(
                processingRunId ->
                    Optional.of(
                        ready(
                            999L
                        )
                    ),
                processingRunId -> {

                    dispatchCalls.incrementAndGet();

                    throw new AssertionError(
                        "dispatch must not execute after "
                            + "readiness identity mismatch"
                    );
                }
            );

        PublicationDispatchJobException exception =
            assertThrows(
                PublicationDispatchJobException.class,
                () ->
                    service.execute(
                        501L
                    )
            );

        assertEquals(
            PublicationDispatchJobException.Reason
                .NOT_READY_INVARIANT,
            exception.reason()
        );

        assertEquals(
            501L,
            exception.processingRunId()
        );

        assertEquals(
            0,
            dispatchCalls.get()
        );
    }

    @Test
    void shouldRejectInvalidProcessingRunIdBeforeQuery() {

        AtomicInteger readinessCalls =
            new AtomicInteger();

        PublicationDispatchJobService service =
            new PublicationDispatchJobService(
                processingRunId -> {

                    readinessCalls.incrementAndGet();

                    return Optional.empty();
                },
                processingRunId -> {

                    throw new AssertionError(
                        "dispatch must not execute"
                    );
                }
            );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.execute(
                    0L
                )
        );

        assertEquals(
            0,
            readinessCalls.get()
        );
    }

    @Test
    void nullDispatchResultShouldBeRejected() {

        PublicationDispatchJobService service =
            new PublicationDispatchJobService(
                processingRunId ->
                    Optional.of(
                        ready(
                            processingRunId
                        )
                    ),
                processingRunId ->
                    null
            );

        assertThrows(
            NullPointerException.class,
            () ->
                service.execute(
                    601L
                )
        );
    }

    private ProcessingRunPublicationReadiness ready(
        long processingRunId
    ) {

        return ProcessingRunPublicationReadiness.from(
            processingRunId,
            ProcessingRunStatus.COMPLETED,
            2L,
            2L,
            0L,
            0L
        );
    }

    private ProcessingRunPublicationReadiness blocked(
        long processingRunId
    ) {

        return ProcessingRunPublicationReadiness.from(
            processingRunId,
            ProcessingRunStatus.COMPLETED,
            2L,
            1L,
            0L,
            1L
        );
    }

    private ProcessingRunPublicationReadiness inProgress(
        long processingRunId
    ) {

        return ProcessingRunPublicationReadiness.from(
            processingRunId,
            ProcessingRunStatus.COMPLETED,
            2L,
            1L,
            1L,
            0L
        );
    }
}
