package com.raspingamazon.application.publication.outbox.resolution;

import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationDeliveryResolutionContractTest {

    @Test
    void shouldMapEveryDecisionToAnExplicitOutboxStatus() {

        assertEquals(
            PublicationOutboxStatus.SUCCEEDED,
            PublicationDeliveryResolutionDecision
                .CONFIRMED_DELIVERED
                .resultingStatus()
        );

        assertEquals(
            PublicationOutboxStatus.PENDING,
            PublicationDeliveryResolutionDecision
                .CONFIRMED_NOT_DELIVERED
                .resultingStatus()
        );

        assertEquals(
            PublicationOutboxStatus.DELIVERY_UNKNOWN,
            PublicationDeliveryResolutionDecision
                .REMAINS_UNKNOWN
                .resultingStatus()
        );
    }

    @Test
    void onlyConfirmedNotDeliveredShouldAllowRedelivery() {

        assertFalse(
            PublicationDeliveryResolutionDecision
                .CONFIRMED_DELIVERED
                .allowsRedelivery()
        );

        assertTrue(
            PublicationDeliveryResolutionDecision
                .CONFIRMED_NOT_DELIVERED
                .allowsRedelivery()
        );

        assertFalse(
            PublicationDeliveryResolutionDecision
                .REMAINS_UNKNOWN
                .allowsRedelivery()
        );
    }

    @Test
    void shouldNormalizeResolutionRequest() {

        PublicationDeliveryResolutionRequest request =
            new PublicationDeliveryResolutionRequest(
                10L,
                "  resolution-10  ",
                PublicationDeliveryResolutionDecision
                    .CONFIRMED_DELIVERED,
                "  operator@example  ",
                "  provider history confirms message  ",
                "  provider-message-123  "
            );

        assertEquals(
            10L,
            request.publicationOutboxId()
        );

        assertEquals(
            "resolution-10",
            request.requestKey()
        );

        assertEquals(
            "operator@example",
            request.decidedBy()
        );

        assertEquals(
            "provider history confirms message",
            request.evidence()
        );

        assertEquals(
            "provider-message-123",
            request.providerReference()
        );
    }

    @Test
    void shouldNormalizeBlankProviderReferenceToNull() {

        PublicationDeliveryResolutionRequest request =
            new PublicationDeliveryResolutionRequest(
                10L,
                "resolution-10",
                PublicationDeliveryResolutionDecision
                    .REMAINS_UNKNOWN,
                "operator",
                "provider has no reconciliation endpoint",
                "   "
            );

        assertNull(
            request.providerReference()
        );
    }

    @Test
    void shouldRejectInvalidResolutionRequest() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationDeliveryResolutionRequest(
                    0L,
                    "request",
                    PublicationDeliveryResolutionDecision
                        .REMAINS_UNKNOWN,
                    "operator",
                    "evidence",
                    null
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationDeliveryResolutionRequest(
                    1L,
                    " ",
                    PublicationDeliveryResolutionDecision
                        .REMAINS_UNKNOWN,
                    "operator",
                    "evidence",
                    null
                )
        );

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationDeliveryResolutionRequest(
                    1L,
                    "request",
                    null,
                    "operator",
                    "evidence",
                    null
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationDeliveryResolutionRequest(
                    1L,
                    "request",
                    PublicationDeliveryResolutionDecision
                        .REMAINS_UNKNOWN,
                    "",
                    "evidence",
                    null
                )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationDeliveryResolutionRequest(
                    1L,
                    "request",
                    PublicationDeliveryResolutionDecision
                        .REMAINS_UNKNOWN,
                    "operator",
                    " ",
                    null
                )
        );
    }

    @Test
    void shouldRepresentNewDecisionAndIdempotentReplay() {

        PublicationDeliveryResolutionResult applied =
            new PublicationDeliveryResolutionResult(
                50L,
                "request-50",
                10L,
                PublicationDeliveryResolutionDecision
                    .CONFIRMED_NOT_DELIVERED,
                PublicationOutboxStatus.PENDING,
                true
            );

        PublicationDeliveryResolutionResult replay =
            new PublicationDeliveryResolutionResult(
                50L,
                "request-50",
                10L,
                PublicationDeliveryResolutionDecision
                    .CONFIRMED_NOT_DELIVERED,
                PublicationOutboxStatus.PENDING,
                false
            );

        assertTrue(
            applied.newlyApplied()
        );

        assertFalse(
            replay.newlyApplied()
        );

        assertEquals(
            applied.resolutionEventId(),
            replay.resolutionEventId()
        );
    }

    @Test
    void shouldRejectResultWhoseStatusDoesNotMatchDecision() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationDeliveryResolutionResult(
                    50L,
                    "request-50",
                    10L,
                    PublicationDeliveryResolutionDecision
                        .CONFIRMED_DELIVERED,
                    PublicationOutboxStatus.PENDING,
                    true
                )
        );
    }
}
