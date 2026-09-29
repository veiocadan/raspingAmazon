package com.raspingamazon.application.publication.outbox.port;

import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Porta da aplicação para consumo da outbox persistente.
 *
 * <p>A aplicação não conhece PostgreSQL, FOR UPDATE,
 * SKIP LOCKED ou qualquer mecanismo concreto de lease.</p>
 */
public interface PublicationOutboxQueuePort {

    /**
     * Reivindica atomicamente a próxima entrada disponível.
     *
     * <p>A implementação deve garantir que dois workers
     * concorrentes não recebam simultaneamente a mesma entrada.</p>
     *
     * <p>O claim deve transformar:</p>
     *
     * <pre>
     * PENDING
     *     ↓
     * PROCESSING
     * </pre>
     *
     * <p>e registrar lockedAt + lockedBy.</p>
     */
    Optional<PublicationOutboxItem> claimNext(
        String workerId,
        OffsetDateTime claimedAt
    );

    /**
     * Recupera leases PROCESSING abandonados.
     *
     * <p>A mesma linha volta para PENDING. Não é criada nova
     * entrada, não é consumida nova quota e a seleção não é
     * recalculada.</p>
     *
     * <p>Somente entradas cujo lockedAt seja menor ou igual ao
     * limite informado são recuperadas.</p>
     *
     * @return número de leases recuperados
     */
    int recoverExpiredLeases(
        OffsetDateTime lockedBefore,
        OffsetDateTime recoveredAt
    );
}
