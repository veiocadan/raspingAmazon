package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.collection.contract.CollectionException;
import com.raspingamazon.application.collection.contract.SourceRestrictionException;
import com.raspingamazon.application.collection.contract.SourceRestrictionType;
import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultProcessingFailureClassifierTest {

    private final DefaultProcessingFailureClassifier classifier =
        new DefaultProcessingFailureClassifier();

    @Test
    void shouldClassifyCollectionTransportFailureAsTransientNetworkFailure() {

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
            OperationalFailureOrigin.EXTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.NETWORK,
            result.category()
        );

        assertEquals(
            "COLLECTION_TRANSPORT",
            result.code()
        );

        assertTrue(
            result.retryable()
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.RETRY
            )
        );
    }

    @Test
    void shouldClassifyRateLimitAsTransientRateLimitFailure() {

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
            OperationalFailureOrigin.EXTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.RATE_LIMIT,
            result.category()
        );

        assertEquals(
            "COLLECTION_HTTP_429",
            result.code()
        );

        assertTrue(
            result.retryable()
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.RETRY
            )
        );
    }

    @Test
    void shouldClassifyForbiddenCollectionAsSourceRestriction() {

        FailureClassification result =
            classifier.classify(
                new CollectionException(
                    "forbidden",
                    403,
                    "access denied"
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            OperationalFailureOrigin.EXTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.SOURCE_RESTRICTION,
            result.category()
        );

        assertEquals(
            "COLLECTION_HTTP_403",
            result.code()
        );

        assertFalse(
            result.retryable()
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.ALERT
            )
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.OPERATOR_INTERVENTION
            )
        );
    }

    @Test
    void shouldClassifyUnauthorizedCollectionAsAuthenticationFailure() {

        FailureClassification result =
            classifier.classify(
                new CollectionException(
                    "unauthorized",
                    401,
                    "invalid credentials"
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            FailureCategory.AUTHENTICATION,
            result.category()
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.PAUSE
            )
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.ALERT
            )
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.OPERATOR_INTERVENTION
            )
        );
    }

    @Test
    void shouldClassifyServerFailureAsTransientNetworkFailure() {

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
            OperationalFailureOrigin.EXTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.NETWORK,
            result.category()
        );

        assertEquals(
            "COLLECTION_HTTP_503",
            result.code()
        );
    }

    @Test
    void shouldClassifyNotFoundAsDataUnavailable() {

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
            FailureCategory.DATA_UNAVAILABLE,
            result.category()
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
    void shouldClassifyOtherClientErrorAsPermanentProcessingFailure() {

        FailureClassification result =
            classifier.classify(
                new CollectionException(
                    "bad request",
                    400,
                    "invalid request"
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            OperationalFailureOrigin.EXTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.PROCESSING,
            result.category()
        );

        assertEquals(
            "COLLECTION_HTTP_400",
            result.code()
        );
    }

    @Test
    void shouldClassifyCaptchaRestrictionAsPermanent() {

        FailureClassification result =
            classifier.classify(
                new SourceRestrictionException(
                    SourceRestrictionType.CAPTCHA
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            OperationalFailureOrigin.EXTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.SOURCE_RESTRICTION,
            result.category()
        );

        assertEquals(
            "SOURCE_RESTRICTION_CAPTCHA",
            result.code()
        );

        assertFalse(
            result.retryable()
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.ALERT
            )
        );
    }

    @Test
    void shouldClassifyChallengeRestrictionAsPermanent() {

        FailureClassification result =
            classifier.classify(
                new SourceRestrictionException(
                    SourceRestrictionType.CHALLENGE
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            FailureCategory.SOURCE_RESTRICTION,
            result.category()
        );

        assertEquals(
            "SOURCE_RESTRICTION_CHALLENGE",
            result.code()
        );

        assertFalse(
            result.retryable()
        );
    }

    @Test
    void shouldClassifyBlockedRestrictionInsideCauseChain() {

        FailureClassification result =
            classifier.classify(
                new RuntimeException(
                    "wrapper",
                    new SourceRestrictionException(
                        SourceRestrictionType.BLOCKED
                    )
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            FailureCategory.SOURCE_RESTRICTION,
            result.category()
        );

        assertEquals(
            "SOURCE_RESTRICTION_BLOCKED",
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
            OperationalFailureOrigin.EXTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.NETWORK,
            result.category()
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
            FailureCategory.NETWORK,
            result.category()
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
            OperationalFailureOrigin.INTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.DATABASE,
            result.category()
        );

        assertEquals(
            "DATABASE_TRANSIENT",
            result.code()
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.RETRY
            )
        );
    }

    @Test
    void shouldClassifyConnectionSqlStateAsTransientWithoutRequiringSubclass() {

        FailureClassification result =
            classifier.classify(
                new SQLException(
                    "connection lost",
                    "08006"
                )
            );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            result.type()
        );

        assertEquals(
            FailureCategory.DATABASE,
            result.category()
        );

        assertEquals(
            "DATABASE_CONNECTION",
            result.code()
        );
    }

    @Test
    void shouldClassifySerializationSqlStateAsTransient() {

        FailureClassification result =
            classifier.classify(
                new SQLException(
                    "serialization failure",
                    "40001"
                )
            );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            result.type()
        );

        assertEquals(
            FailureCategory.DATABASE,
            result.category()
        );

        assertEquals(
            "DATABASE_TRANSACTION_RETRY",
            result.code()
        );
    }

    @Test
    void shouldClassifyDeadlockSqlStateAsTransient() {

        FailureClassification result =
            classifier.classify(
                new SQLException(
                    "deadlock detected",
                    "40P01"
                )
            );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            result.type()
        );

        assertEquals(
            "DATABASE_TRANSACTION_RETRY",
            result.code()
        );

        assertTrue(
            result.retryable()
        );
    }

    @Test
    void shouldClassifyLockNotAvailableSqlStateAsTransient() {

        FailureClassification result =
            classifier.classify(
                new SQLException(
                    "lock not available",
                    "55P03"
                )
            );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            result.type()
        );

        assertEquals(
            "DATABASE_LOCK_NOT_AVAILABLE",
            result.code()
        );
    }

    @Test
    void shouldClassifyPermanentDatabaseSqlStateConservatively() {

        FailureClassification result =
            classifier.classify(
                new SQLException(
                    "unique violation",
                    "23505"
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
            FailureCategory.DATABASE,
            result.category()
        );

        assertEquals(
            "DATABASE_SQLSTATE_23505",
            result.code()
        );

        assertFalse(
            result.retryable()
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.ALERT
            )
        );
    }

    @Test
    void shouldClassifyDatabaseFailureWithoutSqlStateAsPermanent() {

        FailureClassification result =
            classifier.classify(
                new SQLException(
                    "database failure"
                )
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            result.type()
        );

        assertEquals(
            FailureCategory.DATABASE,
            result.category()
        );

        assertEquals(
            "DATABASE_FAILURE",
            result.code()
        );
    }

    @Test
    void shouldClassifyInvalidInputAsPermanentProcessingFailure() {

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
            OperationalFailureOrigin.INTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.PROCESSING,
            result.category()
        );

        assertEquals(
            "INVALID_PROCESSING_INPUT",
            result.code()
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.REJECT
            )
        );
    }

    @Test
    void shouldClassifyInvalidStateAsPermanentAndAlertable() {

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
            OperationalFailureOrigin.INTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.PROCESSING,
            result.category()
        );

        assertEquals(
            "INVALID_PROCESSING_STATE",
            result.code()
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.ALERT
            )
        );
    }

    @Test
    void shouldClassifyUnknownFailureAsPermanentUnknownFailure() {

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
            OperationalFailureOrigin.INTERNAL,
            result.origin()
        );

        assertEquals(
            FailureCategory.UNKNOWN,
            result.category()
        );

        assertEquals(
            "UNCLASSIFIED_FAILURE",
            result.code()
        );

        assertFalse(
            result.retryable()
        );

        assertTrue(
            result.requires(
                FailureHandlingAction.ALERT
            )
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
