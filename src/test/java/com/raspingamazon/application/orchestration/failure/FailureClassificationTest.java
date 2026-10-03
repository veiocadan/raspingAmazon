package com.raspingamazon.application.orchestration.failure;

import com.raspingamazon.application.observability.OperationalFailureOrigin;
import com.raspingamazon.application.orchestration.ProcessingFailureType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureClassificationTest {

    @Test
    void shouldPreserveIndependentOperationalDimensions() {

        FailureClassification classification =
            new FailureClassification(
                ProcessingFailureType.TRANSIENT,
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.RATE_LIMIT,
                EnumSet.of(
                    FailureHandlingAction.RETRY
                ),
                "COLLECTION_HTTP_429",
                "rate limited"
            );

        assertEquals(
            ProcessingFailureType.TRANSIENT,
            classification.type()
        );

        assertEquals(
            OperationalFailureOrigin.EXTERNAL,
            classification.origin()
        );

        assertEquals(
            FailureCategory.RATE_LIMIT,
            classification.category()
        );

        assertEquals(
            Set.of(
                FailureHandlingAction.RETRY
            ),
            classification.handlingActions()
        );

        assertEquals(
            "COLLECTION_HTTP_429",
            classification.code()
        );

        assertEquals(
            "rate limited",
            classification.message()
        );

        assertTrue(
            classification.retryable()
        );

        assertTrue(
            classification.requires(
                FailureHandlingAction.RETRY
            )
        );
    }

    @Test
    void shouldAllowMultiplePermanentHandlingActions() {

        FailureClassification classification =
            new FailureClassification(
                ProcessingFailureType.PERMANENT,
                OperationalFailureOrigin.EXTERNAL,
                FailureCategory.AUTHENTICATION,
                EnumSet.of(
                    FailureHandlingAction.REJECT,
                    FailureHandlingAction.PAUSE,
                    FailureHandlingAction.ALERT,
                    FailureHandlingAction.OPERATOR_INTERVENTION
                ),
                "AUTHENTICATION_FAILED",
                "token rejected"
            );

        assertFalse(
            classification.retryable()
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
    }

    @Test
    void shouldRejectTransientClassificationWithoutRetryAction() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FailureClassification(
                    ProcessingFailureType.TRANSIENT,
                    OperationalFailureOrigin.EXTERNAL,
                    FailureCategory.NETWORK,
                    EnumSet.of(
                        FailureHandlingAction.ALERT
                    ),
                    "NETWORK_TIMEOUT",
                    "timeout"
                )
        );
    }

    @Test
    void shouldRejectPermanentClassificationWithRetryAction() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FailureClassification(
                    ProcessingFailureType.PERMANENT,
                    OperationalFailureOrigin.INTERNAL,
                    FailureCategory.PROCESSING,
                    EnumSet.of(
                        FailureHandlingAction.RETRY
                    ),
                    "INVALID_STATE",
                    "invalid state"
                )
        );
    }

    @Test
    void shouldRejectEmptyHandlingActions() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new FailureClassification(
                    ProcessingFailureType.PERMANENT,
                    OperationalFailureOrigin.INTERNAL,
                    FailureCategory.UNKNOWN,
                    EnumSet.noneOf(
                        FailureHandlingAction.class
                    ),
                    "UNKNOWN",
                    "unknown"
                )
        );
    }

    @Test
    void shouldExposeUnmodifiableHandlingActions() {

        FailureClassification classification =
            new FailureClassification(
                ProcessingFailureType.PERMANENT,
                OperationalFailureOrigin.INTERNAL,
                FailureCategory.PROCESSING,
                EnumSet.of(
                    FailureHandlingAction.REJECT
                ),
                "INVALID_STATE",
                "invalid state"
            );

        assertThrows(
            UnsupportedOperationException.class,
            () ->
                classification
                    .handlingActions()
                    .add(
                        FailureHandlingAction.ALERT
                    )
        );
    }

    @Test
    void legacyTransientConstructorShouldPreserveRetrySemantics() {

        FailureClassification classification =
            new FailureClassification(
                ProcessingFailureType.TRANSIENT,
                "LEGACY_TRANSIENT",
                "temporary"
            );

        assertEquals(
            OperationalFailureOrigin.INTERNAL,
            classification.origin()
        );

        assertEquals(
            FailureCategory.UNKNOWN,
            classification.category()
        );

        assertTrue(
            classification.retryable()
        );

        assertTrue(
            classification.requires(
                FailureHandlingAction.RETRY
            )
        );
    }

    @Test
    void legacyPermanentConstructorShouldPreserveTerminalSemantics() {

        FailureClassification classification =
            new FailureClassification(
                ProcessingFailureType.PERMANENT,
                "LEGACY_PERMANENT",
                "terminal"
            );

        assertEquals(
            OperationalFailureOrigin.INTERNAL,
            classification.origin()
        );

        assertEquals(
            FailureCategory.UNKNOWN,
            classification.category()
        );

        assertFalse(
            classification.retryable()
        );

        assertTrue(
            classification.requires(
                FailureHandlingAction.REJECT
            )
        );
    }
}
