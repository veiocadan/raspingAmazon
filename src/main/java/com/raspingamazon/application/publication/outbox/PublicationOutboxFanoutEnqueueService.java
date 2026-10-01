package com.raspingamazon.application.publication.outbox;

import com.raspingamazon.application.publication.outbox.port.PublicationOutboxDerivedEnqueuePort;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxEnqueuePort;
import com.raspingamazon.application.shared.port.TransactionPort;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Orquestra o enqueue de uma entrega primária e de suas entregas
 * derivadas dentro da mesma unidade transacional.
 *
 * <p>Esta classe implementa o mesmo PublicationOutboxEnqueuePort
 * utilizado pelo fluxo original. Assim o fan-out pode ser introduzido
 * por composição sem obrigar os chamadores existentes a conhecerem
 * a existência das entregas derivadas.</p>
 *
 * <p>Fluxo:</p>
 *
 * <pre>
 * enqueue primário
 *       |
 *       +-- ENQUEUED / ALREADY_ENQUEUED
 *       |       |
 *       |       +--> criar/confirmar derivados
 *       |
 *       +-- QUOTA_EXHAUSTED / STALE_SELECTION
 *               |
 *               +--> não criar derivados
 * </pre>
 *
 * <p>Se qualquer entrega derivada falhar por exceção, a exceção
 * propaga e a TransactionPort decide o rollback da unidade de
 * trabalho.</p>
 */
public final class PublicationOutboxFanoutEnqueueService
    implements PublicationOutboxEnqueuePort {

    private final PublicationOutboxEnqueuePort primaryEnqueuePort;

    private final PublicationOutboxDerivedEnqueuePort
        derivedEnqueuePort;

    private final TransactionPort transactionPort;

    private final List<PublicationOutboxDerivedTarget>
        derivedTargets;

    public PublicationOutboxFanoutEnqueueService(
        PublicationOutboxEnqueuePort primaryEnqueuePort,
        PublicationOutboxDerivedEnqueuePort derivedEnqueuePort,
        TransactionPort transactionPort,
        List<PublicationOutboxDerivedTarget> derivedTargets
    ) {

        this.primaryEnqueuePort =
            Objects.requireNonNull(
                primaryEnqueuePort,
                "primaryEnqueuePort must not be null"
            );

        this.derivedEnqueuePort =
            Objects.requireNonNull(
                derivedEnqueuePort,
                "derivedEnqueuePort must not be null"
            );

        this.transactionPort =
            Objects.requireNonNull(
                transactionPort,
                "transactionPort must not be null"
            );

        Objects.requireNonNull(
            derivedTargets,
            "derivedTargets must not be null"
        );

        this.derivedTargets =
            List.copyOf(
                derivedTargets
            );

        validateDistinctTargets(
            this.derivedTargets
        );
    }

    @Override
    public PublicationOutboxEnqueueResult enqueue(
        PublicationOutboxEnqueueRequest request
    ) {

        Objects.requireNonNull(
            request,
            "request must not be null"
        );

        return transactionPort.execute(
            () ->
                enqueueInsideTransaction(
                    request
                )
        );
    }

    private PublicationOutboxEnqueueResult enqueueInsideTransaction(
        PublicationOutboxEnqueueRequest request
    ) {

        PublicationOutboxEnqueueResult primaryResult =
            Objects.requireNonNull(
                primaryEnqueuePort.enqueue(
                    request
                ),
                "primaryEnqueuePort returned null result"
            );

        /*
         * Não existe entrega externa quando a seleção ficou obsoleta
         * ou quando a quota principal não pôde ser reservada.
         *
         * Portanto nenhum espelho deve nascer nesses casos.
         */
        if (!primaryResult.enqueued()
            && !primaryResult.alreadyEnqueued()) {

            return primaryResult;
        }

        long sourceOutboxId =
            primaryResult.outboxIdValue()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Reserved primary outbox result "
                                + "must contain outboxId"
                        )
                );

        for (PublicationOutboxDerivedTarget target
            : derivedTargets) {

            PublicationOutboxEnqueueResult derivedResult =
                Objects.requireNonNull(
                    derivedEnqueuePort.enqueue(
                        new PublicationOutboxDerivedEnqueueRequest(
                            sourceOutboxId,
                            target.channel(),
                            target.destination(),
                            request.enqueuedAt()
                        )
                    ),
                    "derivedEnqueuePort returned null result"
                );

            validateDerivedResult(
                target,
                derivedResult
            );
        }

        /*
         * O contrato externo continua sendo o resultado da reserva
         * principal. O fan-out é uma consequência operacional dessa
         * reserva, não uma segunda decisão de seleção.
         */
        return primaryResult;
    }

    private void validateDerivedResult(
        PublicationOutboxDerivedTarget target,
        PublicationOutboxEnqueueResult result
    ) {

        if (result.enqueued()
            || result.alreadyEnqueued()) {

            return;
        }

        /*
         * QUOTA_EXHAUSTED e STALE_SELECTION não fazem sentido para
         * uma entrega derivada, pois ela não reserva quota nem
         * executa uma segunda seleção.
         *
         * Receber um desses resultados indica violação do contrato
         * do adapter derivado e deve abortar a unidade transacional.
         */
        throw new IllegalStateException(
            "Derived outbox enqueue returned unsupported status "
                + result.status()
                + " for channel "
                + target.channel()
                + " and destination "
                + target.destination()
        );
    }

    private void validateDistinctTargets(
        List<PublicationOutboxDerivedTarget> targets
    ) {

        Set<DeliveryIdentity> identities =
            new HashSet<>();

        for (PublicationOutboxDerivedTarget target
            : targets) {

            Objects.requireNonNull(
                target,
                "derivedTargets must not contain null"
            );

            DeliveryIdentity identity =
                new DeliveryIdentity(
                    target.channel(),
                    target.destination()
                );

            if (!identities.add(
                identity
            )) {

                throw new IllegalArgumentException(
                    "Duplicate derived delivery target: "
                        + target.channel()
                        + " / "
                        + target.destination()
                );
            }
        }
    }

    private record DeliveryIdentity(
        String channel,
        String destination
    ) {
    }
}
