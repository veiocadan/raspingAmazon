package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.ProcessingRunStatus;
import com.raspingamazon.application.publication.ProcessingRunPublicationReadiness;
import com.raspingamazon.application.publication.PublicationDispatchJobException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationDispatchJobFailureClassificationTest {

    private final DefaultProcessingFailureClassifier classifier =
        new DefaultProcessingFailureClassifier();

    @Test
    void blockedRunShouldBePermanentInternalProcessingFailure() {

        FailureClassification result =
            classifier.classify(
                PublicationDispatchJobException.blocked(
                    ProcessingRunPublicationReadiness.from(
                        101L,
                        ProcessingRunStatus.COMPLETED,
                        1L,
                        0L,
                        0L,
                        1L
                    )
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            OperationalFailureOrigin.INTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.PROCESSING,
            result.category()
        );

        assertEquals(
            "PUBLICATION_DISPATCH_RUN_BLOCKED",
            result.code()
        );

        assertFalse(
            result.retryable()
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.REJECT
            )
        );
    }

    @Test
    void notReadyInvariantShouldBePermanentInternalProcessingFailure() {

        FailureClassification result =
            classifier.classify(
                PublicationDispatchJobException.notReady(
                    ProcessingRunPublicationReadiness.from(
                        201L,
                        ProcessingRunStatus.COMPLETED,
                        1L,
                        0L,
                        1L,
                        0L
                    )
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            OperationalFailureOrigin.INTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.PROCESSING,
            result.category()
        );

        assertEquals(
            "PUBLICATION_DISPATCH_NOT_READY_INVARIANT",
            result.code()
        );

        assertFalse(
            result.retryable()
        );
    }

    @Test
    void missingRunShouldBePermanentInternalProcessingFailure() {

        FailureClassification result =
            classifier.classify(
                PublicationDispatchJobException
                    .processingRunNotFound(
                        301L
                    )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            OperationalFailureOrigin.INTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.PROCESSING,
            result.category()
        );

        assertEquals(
            "PUBLICATION_DISPATCH_RUN_NOT_FOUND",
            result.code()
        );

        assertFalse(
            result.retryable()
        );
    }

    @Test
    void wrappedPublicationDispatchFailureShouldPreserveClassification() {

        FailureClassification result =
            classifier.classify(
                new RuntimeException(
                    "worker wrapper",
                    PublicationDispatchJobException
                        .processingRunNotFound(
                            401L
                        )
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            OperationalFailureOrigin.INTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.PROCESSING,
            result.category()
        );

        assertEquals(
            "PUBLICATION_DISPATCH_RUN_NOT_FOUND",
            result.code()
        );
    }
}
