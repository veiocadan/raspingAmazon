package com.raspingamazon.application.publication.channel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationResultTest {

    @Test
    void shouldCreateSuccessfulResultWithProviderReference() {

        PublicationResult result =
            PublicationResult.success(
                "provider-message-123"
            );

        assertEquals(
            PublicationResultStatus.SUCCESS,
            result.status()
        );

        assertTrue(
            result.successful()
        );

        assertFalse(
            result.transientFailure()
        );

        assertFalse(
            result.permanentFailure()
        );

        assertEquals(
            "provider-message-123",
            result.providerReferenceValue()
                .orElseThrow()
        );

        assertTrue(
            result.errorCodeValue()
                .isEmpty()
        );
    }

    @Test
    void shouldAllowSuccessfulResultWithoutProviderReference() {

        PublicationResult result =
            PublicationResult.success(
                null
            );

        assertTrue(
            result.successful()
        );

        assertTrue(
            result.providerReferenceValue()
                .isEmpty()
        );
    }

    @Test
    void shouldCreateTransientFailure() {

        PublicationResult result =
            PublicationResult.failedTransient(
                "PROVIDER_TEMPORARILY_UNAVAILABLE"
            );

        assertTrue(
            result.transientFailure()
        );

        assertFalse(
            result.successful()
        );

        assertEquals(
            "PROVIDER_TEMPORARILY_UNAVAILABLE",
            result.errorCodeValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldCreatePermanentFailure() {

        PublicationResult result =
            PublicationResult.failedPermanent(
                "INVALID_DESTINATION",
                "provider-request-456"
            );

        assertTrue(
            result.permanentFailure()
        );

        assertEquals(
            "INVALID_DESTINATION",
            result.errorCodeValue()
                .orElseThrow()
        );

        assertEquals(
            "provider-request-456",
            result.providerReferenceValue()
                .orElseThrow()
        );
    }

    @Test
    void shouldRejectSuccessWithErrorCode() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationResult(
                    PublicationResultStatus.SUCCESS,
                    null,
                    "UNEXPECTED_ERROR"
                )
        );
    }

    @Test
    void shouldRejectFailureWithoutErrorCode() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationResult(
                    PublicationResultStatus.FAILED_TRANSIENT,
                    null,
                    null
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationResult(
                    PublicationResultStatus.FAILED_PERMANENT,
                    null,
                    null
                )
        );
    }

    @Test
    void shouldRejectBlankOptionalValues() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                PublicationResult.success(
                    "   "
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                PublicationResult.failedTransient(
                    " "
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                PublicationResult.failedPermanent(
                    "INVALID_DESTINATION",
                    ""
                )
        );
    }

    @Test
    void shouldRejectNullStatus() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationResult(
                    null,
                    null,
                    null
                )
        );
    }
}
