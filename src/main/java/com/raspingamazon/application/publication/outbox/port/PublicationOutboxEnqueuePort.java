package com.raspingamazon.application.publication.outbox.port;

import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;

/**
 * Porta de reserva persistente de trabalho na outbox.
 */
public interface PublicationOutboxEnqueuePort {

    PublicationOutboxEnqueueResult enqueue(
        PublicationOutboxEnqueueRequest request
    );
}
