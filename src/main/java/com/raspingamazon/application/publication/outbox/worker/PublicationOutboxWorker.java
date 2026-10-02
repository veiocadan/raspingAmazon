package com.raspingamazon.application.publication.outbox.worker;

import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationChannelResolver;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxCompletionPort;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxQueuePort;
import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitPolicy;
import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitReservation;
import com.raspingamazon.application.publication.ratelimit.PublicationRateLimitRule;
import com.raspingamazon.application.publication.ratelimit.port.PublicationRateLimitReservationPort;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Worker genérico da outbox de publicação.
 *
 * <p>Executa no máximo uma entrada por chamada de runOnce().</p>
 *
 * <p>Este componente deliberadamente não implementa:</p>
 *
 * <ul>
 *     <li>loop infinito;</li>
 *     <li>sleep;</li>
 *     <li>scheduler;</li>
 *     <li>cálculo de backoff;</li>
 *     <li>Telegram;</li>
 *     <li>WhatsApp.</li>
 * </ul>
 *
 * <p>Quando uma política de rate limiting está configurada, o
 * worker solicita admissão antes de atravessar a fronteira do
 * provider.</p>
 *
 * <p>Se a admissão ainda não estiver disponível, a mesma outbox
 * volta para PENDING com novo availableAt. Nenhuma chamada externa
 * e nenhum PublicationAttempt são produzidos nesse caminho.</p>
 *
 * <p>A composição operacional decide quando executar runOnce().</p>
 *
 * <p>Falhas funcionais conhecidas pelo canal devem ser devolvidas
 * como PublicationResult. Uma RuntimeException inesperada do
 * resolver ou do adapter não é reclassificada artificialmente pelo
 * worker. Nesse caso o lease permanece PROCESSING e poderá ser
 * recuperado pela política de lease da outbox.</p>
 */
public final class PublicationOutboxWorker {

    private final String workerId;

    private final PublicationOutboxQueuePort queue;

    private final PublicationChannelResolver channelResolver;

    private final PublicationOutboxCompletionPort completionPort;

    private final Clock clock;

    private final PublicationRateLimitPolicy rateLimitPolicy;

    private final PublicationRateLimitReservationPort
        rateLimitReservationPort;

    /**
     * Construtor histórico sem rate limiting.
     */
    public PublicationOutboxWorker(
        String workerId,
        PublicationOutboxQueuePort queue,
        PublicationChannelResolver channelResolver,
        PublicationOutboxCompletionPort completionPort,
        Clock clock
    ) {

        this(
            workerId,
            queue,
            channelResolver,
            completionPort,
            clock,
            PublicationRateLimitPolicy.disabled(),
            (
                integrationKey,
                minimumInterval,
                requestedAt
            ) -> {
                throw new IllegalStateException(
                    "Rate-limit reservation port must not be called "
                        + "when rate limiting is disabled"
                );
            }
        );
    }

    /**
     * Construtor completo com rate limiting preventivo.
     */
    public PublicationOutboxWorker(
        String workerId,
        PublicationOutboxQueuePort queue,
        PublicationChannelResolver channelResolver,
        PublicationOutboxCompletionPort completionPort,
        Clock clock,
        PublicationRateLimitPolicy rateLimitPolicy,
        PublicationRateLimitReservationPort rateLimitReservationPort
    ) {

        this.workerId =
            requireText(
                workerId,
                "workerId"
            );

        this.queue =
            Objects.requireNonNull(
                queue,
                "queue must not be null"
            );

        this.channelResolver =
            Objects.requireNonNull(
                channelResolver,
                "channelResolver must not be null"
            );

        this.completionPort =
            Objects.requireNonNull(
                completionPort,
                "completionPort must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        this.rateLimitPolicy =
            Objects.requireNonNull(
                rateLimitPolicy,
                "rateLimitPolicy must not be null"
            );

        this.rateLimitReservationPort =
            Objects.requireNonNull(
                rateLimitReservationPort,
                "rateLimitReservationPort must not be null"
            );
    }

    /**
     * Executa no máximo uma entrada da outbox.
     */
    public PublicationOutboxWorkerRunResult runOnce() {

        OffsetDateTime claimedAt =
            OffsetDateTime.now(
                clock
            );

        Optional<PublicationOutboxItem> claimed =
            queue.claimNext(
                workerId,
                claimedAt
            );

        if (claimed.isEmpty()) {

            return PublicationOutboxWorkerRunResult
                .idle();
        }

        PublicationOutboxItem item =
            claimed.get();

        Optional<PublicationRateLimitRule> rateLimitRule =
            Objects.requireNonNull(
                rateLimitPolicy.findRule(
                    item.channel()
                ),
                "rateLimitPolicy returned null"
            );

        if (rateLimitRule.isPresent()) {

            PublicationRateLimitRule rule =
                rateLimitRule.orElseThrow();

            PublicationRateLimitReservation reservation =
                Objects.requireNonNull(
                    rateLimitReservationPort.reserve(
                        rule.integrationKey(),
                        rule.minimumInterval(),
                        claimedAt
                    ),
                    "rateLimitReservationPort returned null"
                );

            validateReservation(
                rule,
                claimedAt,
                reservation
            );

            if (reservation.deferred()) {

                PublicationOutboxItem deferred =
                    Objects.requireNonNull(
                        queue.deferClaimed(
                            item.id(),
                            workerId,
                            reservation.allowedAt(),
                            claimedAt
                        ),
                        "queue returned null deferred outbox"
                    );

                validateDeferredItem(
                    item,
                    deferred,
                    reservation
                );

                return PublicationOutboxWorkerRunResult
                    .completed(
                        deferred.id(),
                        deferred.status()
                    );
            }
        }

        PublicationChannel channel =
            Objects.requireNonNull(
                channelResolver.resolve(
                    item.channel()
                ),
                "channelResolver returned null PublicationChannel"
            );

        PublicationResult publicationResult =
            Objects.requireNonNull(
                channel.publish(
                    item.command()
                ),
                "PublicationChannel returned null PublicationResult"
            );

        /*
         * A conclusão permanece fora de qualquer catch sobre publish.
         *
         * Se o provider já tiver aceitado a mensagem e a persistência
         * da conclusão falhar, não devemos transformar essa falha de
         * ACK em uma segunda classificação funcional do provider.
         *
         * A entrada permanece protegida pelo mecanismo de lease.
         */
        OffsetDateTime completedAt =
            OffsetDateTime.now(
                clock
            );

        PublicationOutboxItem completed =
            Objects.requireNonNull(
                completionPort.complete(
                    item.id(),
                    workerId,
                    publicationResult,
                    completedAt
                ),
                "completionPort returned null PublicationOutboxItem"
            );

        return PublicationOutboxWorkerRunResult
            .completed(
                completed.id(),
                completed.status()
            );
    }

    private void validateReservation(
        PublicationRateLimitRule rule,
        OffsetDateTime requestedAt,
        PublicationRateLimitReservation reservation
    ) {

        if (!rule.integrationKey()
            .equals(
                reservation.integrationKey()
            )) {

            throw new IllegalStateException(
                "Rate-limit reservation returned unexpected "
                    + "integrationKey"
            );
        }

        if (!requestedAt.equals(
            reservation.requestedAt()
        )) {

            throw new IllegalStateException(
                "Rate-limit reservation returned unexpected "
                    + "requestedAt"
            );
        }
    }

    private void validateDeferredItem(
        PublicationOutboxItem claimed,
        PublicationOutboxItem deferred,
        PublicationRateLimitReservation reservation
    ) {

        if (deferred.id()
            != claimed.id()) {

            throw new IllegalStateException(
                "Deferred outbox identity changed"
            );
        }

        if (deferred.status()
            != PublicationOutboxStatus.PENDING) {

            throw new IllegalStateException(
                "Deferred outbox must return to PENDING"
            );
        }

        if (!deferred.availableAt()
            .equals(
                reservation.allowedAt()
            )) {

            throw new IllegalStateException(
                "Deferred outbox availableAt does not match "
                    + "rate-limit admission time"
            );
        }

        if (deferred.lockedAt() != null
            || deferred.lockedBy() != null) {

            throw new IllegalStateException(
                "Deferred outbox must release worker lease"
            );
        }

        if (deferred.finishedAt() != null) {

            throw new IllegalStateException(
                "Deferred outbox must not be finished"
            );
        }
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return value;
    }
}
