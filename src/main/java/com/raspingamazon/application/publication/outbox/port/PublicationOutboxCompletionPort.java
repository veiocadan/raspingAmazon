package com.raspingamazon.application.publication.outbox.port;

import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;

import java.time.OffsetDateTime;

/**
 * Porta responsável por persistir o resultado de uma execução
 * de PublicationChannel.
 *
 * <p>A operação deve ser atômica:</p>
 *
 * <ul>
 *     <li>registrar PublicationAttempt;</li>
 *     <li>aplicar a transição correspondente da PublicationOutbox;</li>
 *     <li>liberar o lease do worker.</li>
 * </ul>
 *
 * <p>Uma tentativa bem-sucedida ou permanentemente rejeitada
 * finaliza a outbox.</p>
 *
 * <p>Uma falha transitória pode:</p>
 *
 * <ul>
 *     <li>reagendar a mesma outbox como PENDING; ou</li>
 *     <li>finalizá-la como FAILED_TRANSIENT quando o limite de
 *     tentativas tiver sido alcançado.</li>
 * </ul>
 *
 * <p>Somente o worker proprietário da entrada PROCESSING pode
 * concluir a tentativa.</p>
 */
public interface PublicationOutboxCompletionPort {

    /**
     * Persiste o resultado devolvido pelo canal e aplica a transição
     * correspondente da outbox.
     *
     * @param outboxId identidade persistente da entrada
     * @param workerId worker que atualmente possui o lease
     * @param result resultado estruturado do PublicationChannel
     * @param completedAt instante da conclusão da tentativa
     * @return estado persistido da outbox após processar o resultado
     */
    PublicationOutboxItem complete(
        long outboxId,
        String workerId,
        PublicationResult result,
        OffsetDateTime completedAt
    );
}
