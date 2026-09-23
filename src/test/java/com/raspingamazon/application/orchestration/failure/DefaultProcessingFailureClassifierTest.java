package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLTransientConnectionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultProcessingFailureClassifierTest {

    private final DefaultProcessingFailureClassifier classifier =
        new DefaultProcessingFailureClassifier();

    @Test
    void shouldClassifyCollectionTransportFailureAsTransient() {

        FailureClassification result =
            classifier.classify(
                new CollectionException(
                    "collector transport failed"
                )
            );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            result.type()
        );

        assertEquals(
            "COLLECTION_TRANSPORT",
            result.code()
        );

        assertTrue(
            result.retryable()
        );
    }

    @Test
    void shouldClassifyRateLimitAsTransient() {

        FailureClassification result =
            classifier.classify(
                new CollectionException(
                    "rate limited",
                    429,
                    "too many requests"
                )
            );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            result.type()
        );

        assertEquals(
            "COLLECTION_HTTP_429",
            result.code()
        );
    }

    @Test
    void shouldClassifyServerFailureAsTransient() {

        FailureClassification result =
            classifier.classify(
                new CollectionException(
                    "server error",
                    503,
                    "unavailable"
                )
            );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            result.type()
        );

        assertEquals(
            "COLLECTION_HTTP_503",
            result.code()
        );
    }

    @Test
    void shouldClassifyNonRetryableHttpFailureAsPermanent() {

        FailureClassification result =
            classifier.classify(
                new CollectionException(
                    "not found",
                    404,
                    "missing"
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            "COLLECTION_HTTP_404",
            result.code()
        );

        assertFalse(
            result.retryable()
        );
    }

    @Test
    void shouldFindTimeoutInsideCauseChain() {

        FailureClassification result =
            classifier.classify(
                new RuntimeException(
                    "wrapper",
                    new HttpTimeoutException(
                        "timeout"
                    )
                )
            );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            result.type()
        );

        assertEquals(
            "NETWORK_TIMEOUT",
            result.code()
        );
    }

    @Test
    void shouldFindConnectionFailureInsideCauseChain() {

        FailureClassification result =
            classifier.classify(
                new RuntimeException(
                    "wrapper",
                    new ConnectException(
                        "connection refused"
                    )
                )
            );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            result.type()
        );

        assertEquals(
            "NETWORK_CONNECTION_FAILED",
            result.code()
        );
    }

    @Test
    void shouldFindTransientDatabaseFailureInsideCauseChain() {

        FailureClassification result =
            classifier.classify(
                new RuntimeException(
                    "persistence wrapper",
                    new SQLTransientConnectionException(
                        "database unavailable"
                    )
                )
            );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            result.type()
        );

        assertEquals(
            "DATABASE_TRANSIENT",
            result.code()
        );
    }

    @Test
    void shouldClassifyInvalidInputAsPermanent() {

        FailureClassification result =
            classifier.classify(
                new IllegalArgumentException(
                    "invalid id"
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            "INVALID_PROCESSING_INPUT",
            result.code()
        );
    }

    @Test
    void shouldClassifyInvalidStateAsPermanent() {

        FailureClassification result =
            classifier.classify(
                new IllegalStateException(
                    "snapshot missing evidence"
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            "INVALID_PROCESSING_STATE",
            result.code()
        );
    }

    @Test
    void shouldClassifyUnknownFailureAsPermanent() {

        FailureClassification result =
            classifier.classify(
                new RuntimeException(
                    "unexpected bug"
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            "UNCLASSIFIED_FAILURE",
            result.code()
        );
    }

    @Test
    void shouldProvideFallbackMessageWhenExceptionHasNoMessage() {

        FailureClassification result =
            classifier.classify(
                new RuntimeException()
            );

        assertEquals(
            "RuntimeException",
            result.message()
        );
    }
}
