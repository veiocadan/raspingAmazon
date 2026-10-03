package com.raspingamazon.application.publication.outbox.port;

import com.raspingamazon.application.publication.outbox.PublicationAttemptHandle;

import java.time.OffsetDateTime;

/**
 * Porta que estabelece a barreira persistente anterior à chamada
 * externa de publicação.
 *
 * <p>Quando {@link #start(long, String, OffsetDateTime)} retorna com
 * sucesso, a tentativa STARTED deve estar duravelmente confirmada no
 * PostgreSQL.</p>
 *
 * <p>O chamador somente pode atravessar a fronteira externa depois
 * deste método retornar.</p>
 */
public interface PublicationAttemptStartPort {

    /**
     * Persiste o início de uma tentativa física.
     *
     * @param outboxId unidade durável de publicação atualmente PROCESSING
     * @param workerId worker proprietário do lease
     * @param startedAt instante imediatamente anterior à futura chamada
     *                  externa
     * @return identidade persistida da tentativa STARTED
     */
    PublicationAttemptHandle start(
        long outboxId,
        String workerId,
        OffsetDateTime startedAt
    );
}
