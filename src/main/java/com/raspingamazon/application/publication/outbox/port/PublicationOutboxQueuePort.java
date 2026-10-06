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
     * <p>A implementação deve garantir que dois workers concorrentes
     * não recebam simultaneamente a mesma entrada.</p>
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
     * Libera uma entrada PROCESSING que ainda não atravessou a
     * fronteira do provider.
     *
     * <p>Esta operação é usada quando o rate limiter informa que
     * a chamada externa ainda não pode ocorrer.</p>
     *
     * <p>A mesma unidade de trabalho volta para:</p>
     *
     * <pre>
     * PROCESSING
     *     ↓
     * PENDING
     * </pre>
     *
     * <p>com availableAt ajustado para o primeiro instante em que uma
     * nova tentativa de admissão poderá ocorrer.</p>
     *
     * <p>Essa operação:</p>
     *
     * <ul>
     *     <li>não cria PublicationAttempt;</li>
     *     <li>não consome retry;</li>
     *     <li>não cria nova outbox;</li>
     *     <li>não recalcula seleção;</li>
     *     <li>não reserva nova quota.</li>
     * </ul>
     *
     * <p>Somente o worker proprietário do lease pode liberar a
     * entrada.</p>
     *
     * <p>O método default preserva implementações históricas que não
     * participam de um worker com rate limiting habilitado. A
     * implementação JDBC operacional deve sobrescrevê-lo.</p>
     */
    default PublicationOutboxItem deferClaimed(
        long outboxId,
        String workerId,
        OffsetDateTime availableAt,
        OffsetDateTime deferredAt
    ) {

        throw new UnsupportedOperationException(
            "PublicationOutboxQueuePort does not support deferClaimed"
        );
    }

    /**
     * Recupera leases PROCESSING abandonados.
     *
     * <p>A recuperação é consciente da barreira durável de publicação
     * introduzida na FASE 20:</p>
     *
     * <ul>
     *     <li>
     *         PROCESSING sem PublicationAttempt STARTED significa que
     *         a fronteira externa ainda não foi atravessada; a mesma
     *         outbox pode voltar para PENDING;
     *     </li>
     *     <li>
     *         PROCESSING com PublicationAttempt STARTED significa que
     *         a chamada externa pode ter ocorrido; attempt e outbox
     *         devem terminar em DELIVERY_UNKNOWN e não podem ser
     *         reenviados automaticamente.
     *     </li>
     * </ul>
     *
     * <p>Nenhuma nova entrada de outbox é criada, nenhuma nova quota é
     * consumida e a seleção não é recalculada.</p>
     *
     * <p>Somente entradas cujo lockedAt seja menor ou igual ao limite
     * informado participam da recuperação.</p>
     *
     * @return número total de leases recuperados, incluindo tanto os
     *         reencaminhados para PENDING quanto os terminados como
     *         DELIVERY_UNKNOWN
     */
    int recoverExpiredLeases(
        OffsetDateTime lockedBefore,
        OffsetDateTime recoveredAt
    );
}
