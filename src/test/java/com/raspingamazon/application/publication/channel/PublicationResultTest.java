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

        assertFalse(
            result.deliveryUnknown()
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

        assertFalse(
            result.deliveryUnknown()
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

        assertFalse(
            result.deliveryUnknown()
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
    void shouldCreateDeliveryUnknownResult() {

        PublicationResult result =
            PublicationResult.deliveryUnknown(
                "PROVIDER_OUTCOME_UNKNOWN",
                "provider-request-789"
            );

        assertEquals(
            PublicationResultStatus.DELIVERY_UNKNOWN,
            result.status()
        );

        assertTrue(
            result.deliveryUnknown()
        );

        assertFalse(
            result.successful()
        );

        assertFalse(
            result.transientFailure()
        );

        assertFalse(
            result.permanentFailure()
        );

        assertEquals(
            "PROVIDER_OUTCOME_UNKNOWN",
            result.errorCodeValue()
                .orElseThrow()
        );

        assertEquals(
            "provider-request-789",
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
    void shouldRejectNonSuccessWithoutErrorCode() {

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

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationResult(
                    PublicationResultStatus.DELIVERY_UNKNOWN,
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

        assertThrows(
            IllegalArgumentException.class,
            () ->
                PublicationResult.deliveryUnknown(
                    " "
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
