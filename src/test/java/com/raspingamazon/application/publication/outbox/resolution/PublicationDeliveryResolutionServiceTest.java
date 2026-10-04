package com.raspingamazon.application.publication.outbox.resolution;

import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationDeliveryResolutionServiceTest {

    private static final Instant NOW =
        Instant.parse(
            "2026-10-04T03:30:00Z"
        );

    private static final Clock CLOCK =
        Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

    @Test
    void shouldDelegateUsingSingleClockSnapshot() {

        PublicationDeliveryResolutionRequest request =
            request();

        AtomicReference<PublicationDeliveryResolutionRequest>
            capturedRequest =
                new AtomicReference<>();

        AtomicReference<OffsetDateTime>
            capturedResolvedAt =
                new AtomicReference<>();

        PublicationDeliveryResolutionResult expected =
            new PublicationDeliveryResolutionResult(
                90L,
                request.requestKey(),
                request.publicationOutboxId(),
                request.decision(),
                PublicationOutboxStatus.DELIVERY_UNKNOWN,
                true
            );

        PublicationDeliveryResolutionService service =
            new PublicationDeliveryResolutionService(
                (
                    actualRequest,
                    resolvedAt
                ) -> {

                    capturedRequest.set(
                        actualRequest
                    );

                    capturedResolvedAt.set(
                        resolvedAt
                    );

                    return expected;
                },
                CLOCK
            );

        PublicationDeliveryResolutionResult actual =
            service.resolve(
                request
            );

        assertSame(
            request,
            capturedRequest.get()
        );

        assertEquals(
            NOW,
            capturedResolvedAt.get()
                .toInstant()
        );

        assertSame(
            expected,
            actual
        );
    }

    @Test
    void shouldRejectNullRequest() {

        PublicationDeliveryResolutionService service =
            new PublicationDeliveryResolutionService(
                (
                    request,
                    resolvedAt
                ) -> {
                    throw new AssertionError();
                },
                CLOCK
            );

        assertThrows(
            NullPointerException.class,
            () ->
                service.resolve(
                    null
                )
        );
    }

    @Test
    void shouldRejectNullPortResult() {

        PublicationDeliveryResolutionService service =
            new PublicationDeliveryResolutionService(
                (
                    request,
                    resolvedAt
                ) -> null,
                CLOCK
            );

        assertThrows(
            NullPointerException.class,
            () ->
                service.resolve(
                    request()
                )
        );
    }

    private PublicationDeliveryResolutionRequest request() {

        return new PublicationDeliveryResolutionRequest(
            10L,
            "resolution-10",
            PublicationDeliveryResolutionDecision
                .REMAINS_UNKNOWN,
            "operator",
            "no reliable provider evidence is available",
            null
        );
    }
}
