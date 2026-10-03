package com.raspingamazon.application.publication.outbox.port;

import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.outbox.PublicationAttemptHandle;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;

import java.time.OffsetDateTime;

/**
 * Porta responsável por concluir uma tentativa física cuja existência
 * já foi persistida como STARTED antes da chamada externa.
 *
 * <p>Esta operação nunca cria uma nova PublicationAttempt.</p>
 *
 * <p>Ela deve:</p>
 *
 * <ol>
 *     <li>validar que a outbox continua PROCESSING e pertence ao worker;</li>
 *     <li>localizar exatamente a tentativa identificada pelo handle;</li>
 *     <li>exigir status STARTED;</li>
 *     <li>transformar a tentativa no resultado conhecido;</li>
 *     <li>aplicar retry ou terminalidade na mesma transação;</li>
 *     <li>liberar o lease da outbox.</li>
 * </ol>
 */
public interface PublicationAttemptCompletionPort {

    PublicationOutboxItem complete(
        PublicationAttemptHandle attempt,
        String workerId,
        PublicationResult result,
        OffsetDateTime completedAt
    );
}
