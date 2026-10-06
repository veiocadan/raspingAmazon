package com.raspingamazon.application.publication.outbox.resolution;

import com.raspingamazon.application.publication.outbox.resolution.port.PublicationDeliveryResolutionPort;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Caso de uso da resolução operacional de entrega ambígua.
 */
public final class PublicationDeliveryResolutionService {

    private final PublicationDeliveryResolutionPort resolutionPort;

    private final Clock clock;

    public PublicationDeliveryResolutionService(
        PublicationDeliveryResolutionPort resolutionPort,
        Clock clock
    ) {

        this.resolutionPort =
            Objects.requireNonNull(
                resolutionPort,
                "resolutionPort must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    public PublicationDeliveryResolutionResult resolve(
        PublicationDeliveryResolutionRequest request
    ) {

        PublicationDeliveryResolutionRequest validatedRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        OffsetDateTime resolvedAt =
            OffsetDateTime.now(
                clock
            );

        return Objects.requireNonNull(
            resolutionPort.resolve(
                validatedRequest,
                resolvedAt
            ),
            "resolutionPort returned null"
        );
    }
}
