package com.raspingamazon.application.orchestration;

import com.raspingamazon.application.orchestration.worker.DefaultProcessingJobExecutor;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationDispatchProcessingJobContractTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.parse(
            "2026-10-01T19:00:00-03:00"
        );

    @Test
    void publicationDispatchSubmissionShouldUseProcessingRunAsSubject() {

        ProcessingJobSubmission submission =
            ProcessingJobSubmission.publicationDispatch(
                71L,
                "publication-dispatch:71",
                5,
                NOW
            );

        assertEquals(
            ProcessingJobType.PUBLICATION_DISPATCH,
            submission.type()
        );

        assertEquals(
            71L,
            submission.processingRunId()
        );

        assertEquals(
            null,
            submission.dealCandidateId()
        );

        assertEquals(
            null,
            submission.offerSnapshotId()
        );
    }

    @Test
    void publicationDispatchSubmissionShouldRejectInvalidRunId() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                ProcessingJobSubmission.publicationDispatch(
                    0L,
                    "publication-dispatch:0",
                    5,
                    NOW
                )
        );
    }

    @Test
    void publicationDispatchProcessingJobShouldAcceptValidSubject() {

        ProcessingJob job =
            publicationDispatchJob(
                81L
            );

        assertEquals(
            ProcessingJobType.PUBLICATION_DISPATCH,
            job.type()
        );

        assertEquals(
            81L,
            job.processingRunId()
        );
    }

    @Test
    void publicationDispatchProcessingJobShouldRequireProcessingRun() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ProcessingJob(
                    900L,
                    ProcessingJobType.PUBLICATION_DISPATCH,
                    ProcessingJobStatus.PENDING,
                    null,
                    null,
                    null,
                    "publication-dispatch:missing-run",
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
                )
        );
    }

    @Test
    void executorShouldDispatchPublicationJobToPublicationHandler() {

        AtomicLong handledRunId =
            new AtomicLong();

        DefaultProcessingJobExecutor executor =
            new DefaultProcessingJobExecutor(
                ignored -> {
                },
                ignored -> {
                },
                ignored -> {
                },
                handledRunId::set
            );

        executor.execute(
            publicationDispatchJob(
                91L
            )
        );

        assertEquals(
            91L,
            handledRunId.get()
        );
    }

    @Test
    void legacyExecutorConstructorShouldFailExplicitlyForPublicationJob() {

        DefaultProcessingJobExecutor executor =
            new DefaultProcessingJobExecutor(
                ignored -> {
                },
                ignored -> {
                },
                ignored -> {
                }
            );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    executor.execute(
                        publicationDispatchJob(
                            101L
                        )
                    )
            );

        assertEquals(
            "PUBLICATION_DISPATCH handler is not configured",
            exception.getMessage()
        );
    }

    private ProcessingJob publicationDispatchJob(
        long processingRunId
    ) {

        return new ProcessingJob(
            processingRunId + 1000L,
            ProcessingJobType.PUBLICATION_DISPATCH,
            ProcessingJobStatus.PENDING,
            processingRunId,
            null,
            null,
            "publication-dispatch:" + processingRunId,
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
    }
}
