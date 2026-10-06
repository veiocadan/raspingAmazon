package com.raspingamazon.application.publication.outbox.resolution.port;

import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionRequest;
import com.raspingamazon.application.publication.outbox.resolution.PublicationDeliveryResolutionResult;

import java.time.OffsetDateTime;

/**
 * Porta de resolução controlada de DELIVERY_UNKNOWN.
 *
 * <p>A implementação deve ser transacional e idempotente por
 * requestKey.</p>
 *
 * <p>Somente uma publication_outbox atualmente em DELIVERY_UNKNOWN
 * pode receber uma nova decisão. Replay da mesma requestKey pode ser
 * retornado mesmo depois que a outbox tenha avançado.</p>
 */
public interface PublicationDeliveryResolutionPort {

    PublicationDeliveryResolutionResult resolve(
        PublicationDeliveryResolutionRequest request,
        OffsetDateTime resolvedAt
    );
}
