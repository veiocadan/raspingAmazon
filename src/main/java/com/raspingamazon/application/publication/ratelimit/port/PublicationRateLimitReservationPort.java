package com.raspingamazon.application.publication.ratelimit.port;

import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitReservation;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Reserva atomicamente um slot de chamada para uma integração
 * externa de publicação.
 *
 * <p>A implementação deve ser segura para múltiplos workers e
 * múltiplas instâncias da aplicação.</p>
 */
@FunctionalInterface
public interface PublicationRateLimitReservationPort {

    PublicationRateLimitReservation reserve(
        String integrationKey,
        Duration minimumInterval,
        OffsetDateTime requestedAt
    );
}
