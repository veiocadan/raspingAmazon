package com.raspingamazon.application.orchestration;

import com.raspingamazon.application.parsing.contract.ParsedDeal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingOrchestrationContractsTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-09-22T18:00:00-03:00"
        );

    private static final OffsetDateTime LATER =
        OffsetDateTime.parse(
            "2026-09-22T18:05:00-03:00"
        );

    @Test
    void shouldRepresentTransientDealCandidate() {

        DealCandidate candidate =
            new DealCandidate(
                null,
                10L,
                createParsedDeal()
            );

        assertFalse(
            candidate.persisted()
        );

        assertEquals(
            10L,
            candidate.processingRunId()
        );
    }

    @Test
    void shouldRepresentPersistedDealCandidate() {

        DealCandidate candidate =
            new DealCandidate(
                20L,
                10L,
                createParsedDeal()
            );

        assertTrue(
            candidate.persisted()
        );

        assertEquals(
            20L,
            candidate.id()
        );
    }

    @Test
    void shouldRejectDealCandidateWithoutValidRun() {

        assertThrows(
            IllegalArgumentException.class,
            () -> new DealCandidate(
                null,
                0L,
                createParsedDeal()
            )
        );
    }

    @Test
    void shouldRepresentPendingProcessingRun() {

        ProcessingRun run =
            new ProcessingRun(
                null,
                "collection:2026-09-22T18:00:00-03:00",
                URI.create(
                    "https://www.amazon.com.br/deals"
                ),
                ProcessingRunStatus.PENDING,
                NOW,
                null,
                null,
                null,
                null
            );

        assertFalse(
            run.persisted()
        );

        assertEquals(
            ProcessingRunStatus.PENDING,
            run.status()
        );
    }

    @Test
    void shouldRepresentRunningProcessingRun() {

        ProcessingRun run =
            new ProcessingRun(
                30L,
                "collection:30",
                URI.create(
                    "https://www.amazon.com.br/deals"
                ),
                ProcessingRunStatus.RUNNING,
                NOW,
                LATER,
                null,
                null,
                null
            );

        assertTrue(
            run.persisted()
        );

        assertEquals(
            LATER,
            run.startedAt()
        );
    }

    @Test
    void shouldRejectCompletedRunWithoutLifecycleDates() {

        assertThrows(
            NullPointerException.class,
            () -> new ProcessingRun(
                30L,
                "collection:30",
                URI.create(
                    "https://www.amazon.com.br/deals"
                ),
                ProcessingRunStatus.COMPLETED,
                NOW,
                null,
                null,
                null,
                null
            )
        );
    }

    @Test
    void shouldCreateCollectionSubmission() {

        ProcessingJobSubmission submission =
            ProcessingJobSubmission.collectDeals(
                30L,
                "collect:30",
                5,
                NOW
            );

        assertEquals(
            ProcessingJobType.COLLECT_DEALS,
            submission.type()
        );

        assertEquals(
            30L,
            submission.processingRunId()
        );

        assertNull(
            submission.dealCandidateId()
        );

        assertNull(
            submission.offerSnapshotId()
        );
    }

    @Test
    void shouldCreateEnrichmentSubmission() {

        ProcessingJobSubmission submission =
            ProcessingJobSubmission.enrichDeal(
                40L,
                "enrich:40",
                5,
                NOW
            );

        assertEquals(
            ProcessingJobType.ENRICH_DEAL,
            submission.type()
        );

        assertEquals(
            40L,
            submission.dealCandidateId()
        );
    }

    @Test
    void shouldCreateEvaluationSubmission() {

        ProcessingJobSubmission submission =
            ProcessingJobSubmission.evaluateDeal(
                50L,
                "evaluate:50",
                5,
                NOW
            );

        assertEquals(
            ProcessingJobType.EVALUATE_DEAL,
            submission.type()
        );

        assertEquals(
            50L,
            submission.offerSnapshotId()
        );
    }

    @Test
    void shouldRepresentPendingCollectionJob() {

        ProcessingJob job =
            new ProcessingJob(
                60L,
                ProcessingJobType.COLLECT_DEALS,
                ProcessingJobStatus.PENDING,
                30L,
                null,
                null,
                "collect:30",
                0,
                5,
                NOW,
                null,
                null,
                null,
                null,
                null,
                NOW,
                NOW,
                null
            );

        assertFalse(
            job.finished()
        );

        assertTrue(
            job.canRetry()
        );
    }

    @Test
    void shouldRepresentRunningEnrichmentJob() {

        ProcessingJob job =
            new ProcessingJob(
                61L,
                ProcessingJobType.ENRICH_DEAL,
                ProcessingJobStatus.RUNNING,
                null,
                40L,
                null,
                "enrich:40",
                1,
                5,
                NOW,
                NOW,
                "worker-1",
                null,
                null,
                null,
                NOW,
                NOW,
                null
            );

        assertEquals(
            ProcessingJobStatus.RUNNING,
            job.status()
        );

        assertEquals(
            "worker-1",
            job.lockedBy()
        );

        assertTrue(
            job.canRetry()
        );
    }

    @Test
    void shouldRepresentSucceededEvaluationJob() {

        ProcessingJob job =
            new ProcessingJob(
                62L,
                ProcessingJobType.EVALUATE_DEAL,
                ProcessingJobStatus.SUCCEEDED,
                null,
                null,
                50L,
                "evaluate:50",
                1,
                5,
                NOW,
                null,
                null,
                null,
                null,
                null,
                NOW,
                LATER,
                LATER
            );

        assertTrue(
            job.finished()
        );

        assertTrue(
            job.canRetry()
        );
    }

    private static ParsedDeal createParsedDeal() {

        return new ParsedDeal(
            "B087WLJH8Y",
            "https://www.amazon.com.br/dp/B087WLJH8Y",
            "Produto de teste",
            "https://example.com/image.jpg",
            new BigDecimal(
                "99.90"
            ),
            new BigDecimal(
                "129.90"
            ),
            new BigDecimal(
                "119.90"
            ),
            new BigDecimal(
                "42.00"
            ),
            4.6,
            58363L,
            NOW,
            "https://www.amazon.com.br/deals"
        );
    }
}
