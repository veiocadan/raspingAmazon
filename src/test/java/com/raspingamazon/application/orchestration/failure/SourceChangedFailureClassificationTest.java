package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.collection.contract.SourceChangedException;
import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import com.raspingamazon.infrastructure.amazon.enrichment.ProductPageContentProviderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceChangedFailureClassificationTest {

    private final DefaultProcessingFailureClassifier classifier =
        new DefaultProcessingFailureClassifier();

    @Test
    void shouldClassifySourceChangeAsPermanentExternalFailure() {

        SourceChangedException failure =
            new SourceChangedException(
                "AMAZON_PRODUCT_PAGE_STRUCTURE_UNRECOGNIZED",
                "Amazon product structure changed"
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
            FailureCategory.SOURCE_CHANGED,
            classification.category()
        );

        assertEquals(
            "AMAZON_PRODUCT_PAGE_STRUCTURE_UNRECOGNIZED",
            classification.code()
        );

        assertFalse(
            classification.retryable()
        );
    }

    @Test
    void sourceChangeShouldRequireFailClosedOperationalActions() {

        SourceChangedException failure =
            new SourceChangedException(
                "AMAZON_PRODUCT_PAGE_STRUCTURE_UNRECOGNIZED",
                "Amazon product structure changed"
            );

        FailureClassification classification =
            classifier.classify(
                failure
            );

        assertTrue(
            classification.requires(
                FailureHandlingAction.REJECT
            )
        );

        assertTrue(
            classification.requires(
                FailureHandlingAction.PAUSE
            )
        );

        assertTrue(
            classification.requires(
                FailureHandlingAction.ALERT
            )
        );

        assertTrue(
            classification.requires(
                FailureHandlingAction.OPERATOR_INTERVENTION
            )
        );

        assertFalse(
            classification.requires(
                FailureHandlingAction.RETRY
            )
        );
    }

    @Test
    void shouldFindSourceChangeInsideProviderWrapping() {

        SourceChangedException sourceChanged =
            new SourceChangedException(
                "AMAZON_PRODUCT_PAGE_STRUCTURE_UNRECOGNIZED",
                "Amazon product structure changed"
            );

        ProductPageContentProviderException wrapper =
            new ProductPageContentProviderException(
                "Amazon product page failed source validation",
                sourceChanged
            );

        FailureClassification classification =
            classifier.classify(
                wrapper
            );

        assertEquals(
            FailureCategory.SOURCE_CHANGED,
            classification.category()
        );

        assertEquals(
            "AMAZON_PRODUCT_PAGE_STRUCTURE_UNRECOGNIZED",
            classification.code()
        );
    }
}
