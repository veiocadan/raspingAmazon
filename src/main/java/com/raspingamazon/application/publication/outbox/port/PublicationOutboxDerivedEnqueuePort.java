package com.raspingamazon.application.publication.outbox.port;

import com.raspingamazon.application.publication.outbox.PublicationOutboxDerivedEnqueueRequest;
import com.raspingamazon.application.publication.outbox.PublicationOutboxEnqueueResult;

/**
 * Porta responsável por criar uma entrega derivada de uma entrada
 * primária já reservada na publication_outbox.
 *
 * <p>A entrega derivada:</p>
 *
 * <ul>
 *     <li>preserva Publication;</li>
 *     <li>preserva SelectionRun;</li>
 *     <li>preserva selectionPosition;</li>
 *     <li>preserva conteúdo;</li>
 *     <li>preserva availableAt;</li>
 *     <li>não reserva quota adicional.</li>
 * </ul>
 */
public interface PublicationOutboxDerivedEnqueuePort {

    PublicationOutboxEnqueueResult enqueue(
        PublicationOutboxDerivedEnqueueRequest request
    );
}
