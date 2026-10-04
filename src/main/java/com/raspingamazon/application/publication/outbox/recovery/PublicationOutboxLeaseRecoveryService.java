package com.raspingamazon.application.publication.outbox.recovery;

import com.raspingamazon.application.publication.outbox.port.PublicationOutboxQueuePort;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Caso de uso responsável por executar uma rodada de recuperação
 * de leases expirados da publication_outbox.
 *
 * <p>A política temporal pertence à camada de aplicação. O adapter
 * recebe somente os instantes já calculados.</p>
 *
 * <p>A semântica concreta da recuperação permanece no contrato da
 * publication outbox:</p>
 *
 * <ul>
 *     <li>
 *         PROCESSING expirado sem STARTED pode voltar para PENDING;
 *     </li>
 *     <li>
 *         PROCESSING expirado com STARTED deve terminar como
 *         DELIVERY_UNKNOWN.
 *     </li>
 * </ul>
 */
public final class PublicationOutboxLeaseRecoveryService {

    private final PublicationOutboxQueuePort queue;

    private final Duration leaseDuration;

    private final Clock clock;

    public PublicationOutboxLeaseRecoveryService(
        PublicationOutboxQueuePort queue,
        Duration leaseDuration,
        Clock clock
    ) {

        this.queue =
            Objects.requireNonNull(
                queue,
                "queue must not be null"
            );

        this.leaseDuration =
            requirePositiveDuration(
                leaseDuration
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    /**
     * Executa uma única rodada de recuperação.
     *
     * <p>O serviço não executa publicação, não cria nova outbox e
     * não decide retry. Ele apenas aciona a política de recuperação
     * persistente já definida pela porta.</p>
     */
    public PublicationOutboxLeaseRecoveryResult recoverOnce() {

        OffsetDateTime recoveredAt =
            OffsetDateTime.now(
                clock
            );

        OffsetDateTime leaseExpiredBefore =
            recoveredAt.minus(
                leaseDuration
            );

        int recoveredLeaseCount =
            queue.recoverExpiredLeases(
                leaseExpiredBefore,
                recoveredAt
            );

        if (recoveredLeaseCount < 0) {

            throw new IllegalStateException(
                "Publication outbox recovery returned "
                    + "a negative recovered lease count"
            );
        }

        return new PublicationOutboxLeaseRecoveryResult(
            recoveredLeaseCount
        );
    }

    private static Duration requirePositiveDuration(
        Duration value
    ) {

        Objects.requireNonNull(
            value,
            "leaseDuration must not be null"
        );

        if (value.isZero()
            || value.isNegative()) {

            throw new IllegalArgumentException(
                "leaseDuration must be positive"
            );
        }

        return value;
    }
}
