package com.raspingamazon.infrastructure.amazon.enrichment;

import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.application.orchestration.failure.DefaultProcessingFailureClassifier;
import com.raspingamazon.application.orchestration.failure.FailureCategory;
import com.raspingamazon.application.orchestration.failure.FailureClassification;
import com.raspingamazon.application.orchestration.failure.FailureHandlingAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garante que a fronteira de aquisição da página individual preserve
 * informação suficiente para utilizar a mesma taxonomia operacional
 * do restante do pipeline.
 */
class ProductPageContentProviderFailureClassificationTest {

    private final DefaultProcessingFailureClassifier classifier =
        new DefaultProcessingFailureClassifier();

    @Test
    void rateLimitShouldBeTransientRateLimitFailure() {

        ProductPageContentProviderException failure =
            new ProductPageContentProviderException(
                "Product page returned HTTP 429",
                429,
                "Too many requests"
            );

        FailureClassification result =
            classifier.classify(
                failure
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
    void remoteServerFailureShouldBeTransientNetworkFailure() {

        ProductPageContentProviderException failure =
            new ProductPageContentProviderException(
                "Product page returned HTTP 503",
                503,
                "Service unavailable"
            );

        FailureClassification result =
            classifier.classify(
                failure
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

        assertTrue(
            result.retryable()
        );
    }

    @Test
    void missingProductShouldBePermanentDataUnavailableFailure() {

        ProductPageContentProviderException failure =
            new ProductPageContentProviderException(
                "Product page returned HTTP 404",
                404,
                "Product not found"
            );

        FailureClassification result =
            classifier.classify(
                failure
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

        assertTrue(
            result.requires(
                FailureHandlingAction.REJECT
            )
        );
    }

    @Test
    void forbiddenProductPageShouldBeSourceRestrictionFailure() {

        ProductPageContentProviderException failure =
            new ProductPageContentProviderException(
                "Product page returned HTTP 403",
                403,
                "Forbidden"
            );

        FailureClassification result =
            classifier.classify(
                failure
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
}
