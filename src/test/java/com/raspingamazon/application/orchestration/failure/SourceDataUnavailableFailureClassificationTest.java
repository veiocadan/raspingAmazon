package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.collection.contract.SourceDataUnavailableException;
import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.infrastructure.amazon.enrichment.ProductPageContentProviderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceDataUnavailableFailureClassificationTest {

    private final DefaultProcessingFailureClassifier classifier =
        new DefaultProcessingFailureClassifier();

    @Test
    void shouldClassifyUnavailableSourceDataAsPermanentExternalFailure() {

        SourceDataUnavailableException failure =
            new SourceDataUnavailableException(
                "AMAZON_PRODUCT_PAGE_NOT_FOUND",
                "Amazon product does not exist"
            );

        FailureClassification classification =
            classifier.classify(
                failure
            );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            classification.type()
        );

        assertEquals(
            OperationalFailureOrigin.EXTERNAL,
            classification.origin()
        );

        assertEquals(
            FailureCategory.DATA_UNAVAILABLE,
            classification.category()
        );

        assertEquals(
            "AMAZON_PRODUCT_PAGE_NOT_FOUND",
            classification.code()
        );

        assertFalse(
            classification.retryable()
        );

        assertTrue(
            classification.requires(
                FailureHandlingAction.REJECT
            )
        );

        assertFalse(
            classification.requires(
                FailureHandlingAction.RETRY
            )
        );

        assertFalse(
            classification.requires(
                FailureHandlingAction.PAUSE
            )
        );
    }

    @Test
    void shouldFindUnavailableDataInsideProviderWrapping() {

        SourceDataUnavailableException unavailable =
            new SourceDataUnavailableException(
                "AMAZON_PRODUCT_PAGE_NOT_FOUND",
                "Amazon product does not exist"
            );

        ProductPageContentProviderException wrapper =
            new ProductPageContentProviderException(
                "Failed to render Amazon product page",
                unavailable
            );

        FailureClassification classification =
            classifier.classify(
                wrapper
            );

        assertEquals(
            FailureCategory.DATA_UNAVAILABLE,
            classification.category()
        );

        assertEquals(
            "AMAZON_PRODUCT_PAGE_NOT_FOUND",
            classification.code()
        );

        assertEquals(
            ProcessingFailureType.PERMANENT,
            classification.type()
        );
    }
}
