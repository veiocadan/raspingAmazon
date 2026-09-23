package com.raspingamazon.application.orchestration.worker;

import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobStatus;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultProcessingJobExecutorTest {

    private static final OffsetDateTime AVAILABLE_AT =
        OffsetDateTime.parse(
            "2026-09-23T00:00:00Z"
        );

    @Test
    void shouldDispatchCollectDealsJobUsingProcessingRunId() {

        AtomicLong capturedRunId =
            new AtomicLong();

        AtomicInteger enrichCalls =
            new AtomicInteger();

        AtomicInteger evaluationCalls =
            new AtomicInteger();

        DefaultProcessingJobExecutor executor =
            new DefaultProcessingJobExecutor(
                capturedRunId::set,
                id -> enrichCalls.incrementAndGet(),
                id -> evaluationCalls.incrementAndGet()
            );

        executor.execute(
            collectJob(
                101L
            )
        );

        assertEquals(
            101L,
            capturedRunId.get()
        );

        assertEquals(
            0,
            enrichCalls.get()
        );

        assertEquals(
            0,
            evaluationCalls.get()
        );
    }

    @Test
    void shouldDispatchEnrichDealJobUsingCandidateId() {

        AtomicInteger collectionCalls =
            new AtomicInteger();

        AtomicLong capturedCandidateId =
            new AtomicLong();

        AtomicInteger evaluationCalls =
            new AtomicInteger();

        DefaultProcessingJobExecutor executor =
            new DefaultProcessingJobExecutor(
                id -> collectionCalls.incrementAndGet(),
                capturedCandidateId::set,
                id -> evaluationCalls.incrementAndGet()
            );

        executor.execute(
            enrichmentJob(
                202L
            )
        );

        assertEquals(
            0,
            collectionCalls.get()
        );

        assertEquals(
            202L,
            capturedCandidateId.get()
        );

        assertEquals(
            0,
            evaluationCalls.get()
        );
    }

    @Test
    void shouldDispatchEvaluateDealJobUsingOfferSnapshotId() {

        AtomicInteger collectionCalls =
            new AtomicInteger();

        AtomicInteger enrichmentCalls =
            new AtomicInteger();

        AtomicLong capturedSnapshotId =
            new AtomicLong();

        DefaultProcessingJobExecutor executor =
            new DefaultProcessingJobExecutor(
                id -> collectionCalls.incrementAndGet(),
                id -> enrichmentCalls.incrementAndGet(),
                capturedSnapshotId::set
            );

        executor.execute(
            evaluationJob(
                303L
            )
        );

        assertEquals(
            0,
            collectionCalls.get()
        );

        assertEquals(
            0,
            enrichmentCalls.get()
        );

        assertEquals(
            303L,
            capturedSnapshotId.get()
        );
    }

    @Test
    void shouldRejectNullJob() {

        DefaultProcessingJobExecutor executor =
            new DefaultProcessingJobExecutor(
                id -> {
                },
                id -> {
                },
                id -> {
                }
            );

        assertThrows(
            NullPointerException.class,
            () -> executor.execute(
                null
            )
        );
    }

    private static ProcessingJob collectJob(
        long processingRunId
    ) {

        return new ProcessingJob(
            1L,
            ProcessingJobType.COLLECT_DEALS,
            ProcessingJobStatus.PENDING,
            processingRunId,
            null,
            null,
            "collect:" + processingRunId,
            0,
            5,
            AVAILABLE_AT,
            null,
            null,
            null,
            null,
            null,
            AVAILABLE_AT,
            AVAILABLE_AT,
            null
        );
    }

    private static ProcessingJob enrichmentJob(
        long dealCandidateId
    ) {

        return new ProcessingJob(
            2L,
            ProcessingJobType.ENRICH_DEAL,
            ProcessingJobStatus.PENDING,
            null,
            dealCandidateId,
            null,
            "enrich:" + dealCandidateId,
            0,
            5,
            AVAILABLE_AT,
            null,
            null,
            null,
            null,
            null,
            AVAILABLE_AT,
            AVAILABLE_AT,
            null
        );
    }

    private static ProcessingJob evaluationJob(
        long offerSnapshotId
    ) {

        return new ProcessingJob(
            3L,
            ProcessingJobType.EVALUATE_DEAL,
            ProcessingJobStatus.PENDING,
            null,
            null,
            offerSnapshotId,
            "evaluate:" + offerSnapshotId,
            0,
            5,
            AVAILABLE_AT,
            null,
            null,
            null,
            null,
            null,
            AVAILABLE_AT,
            AVAILABLE_AT,
            null
        );
    }
}
