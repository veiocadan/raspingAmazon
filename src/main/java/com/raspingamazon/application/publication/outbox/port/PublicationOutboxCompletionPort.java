package com.raspingamazon.application.publication.outbox.port;

import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;

import java.time.OffsetDateTime;

/**
 * Porta responsável por persistir o resultado de uma execução
 * de PublicationChannel.
 *
 * <p>A conclusão deve ser atômica:</p>
 *
 * <ul>
 *     <li>registrar PublicationAttempt;</li>
 *     <li>finalizar PublicationOutbox;</li>
 *     <li>liberar o lease do worker.</li>
 * </ul>
 *
 * <p>Somente o worker proprietário da entrada PROCESSING pode
 * concluí-la.</p>
 */
public interface PublicationOutboxCompletionPort {

    /**
     * Persiste o resultado devolvido pelo canal e finaliza a entrada
     * da outbox.
     *
     * @param outboxId identidade persistente da entrada
     * @param workerId worker que atualmente possui o lease
     * @param result resultado estruturado do PublicationChannel
     * @param completedAt instante da conclusão
     * @return estado persistido da outbox após a conclusão
     */
    PublicationOutboxItem complete(
        long outboxId,
        String workerId,
        PublicationResult result,
        OffsetDateTime completedAt
    );
}
