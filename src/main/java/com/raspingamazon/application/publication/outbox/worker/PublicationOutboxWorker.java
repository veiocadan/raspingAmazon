package com.raspingamazon.application.publication.outbox.worker;

import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationChannelResolver;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.outbox.PublicationAttemptHandle;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;
import com.raspingamazon.application.publication.outbox.port.PublicationAttemptCompletionPort;
import com.raspingamazon.application.publication.outbox.port.PublicationAttemptStartPort;
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
 * <p>O protocolo operacional da FASE 20 estabelece uma barreira
 * persistente obrigatória antes de qualquer efeito externo:</p>
 *
 * <pre>
 * claim
 *     ↓
 * rate-limit admission
 *     ↓
 * resolve channel
 *     ↓
 * PublicationAttempt STARTED + commit
 *     ↓
 * PublicationChannel.publish()
 *     ↓
 * concluir o MESMO PublicationAttempt
 * </pre>
 *
 * <p>Consequentemente:</p>
 *
 * <ul>
 *     <li>
 *         nenhuma chamada ao provider pode acontecer sem uma tentativa
 *         STARTED previamente persistida;
 *     </li>
 *     <li>
 *         falha de resolução do canal ocorre antes de STARTED;
 *     </li>
 *     <li>
 *         defer por rate limit ocorre antes de STARTED;
 *     </li>
 *     <li>
 *         falha inesperada durante o provider deixa STARTED durável;
 *     </li>
 *     <li>
 *         falha de persistência após o provider também deixa STARTED
 *         durável.
 *     </li>
 * </ul>
 *
 * <p>Os dois últimos casos serão reconciliados pela recuperação de
 * lease da FASE 20-D1B-3 como DELIVERY_UNKNOWN, sem retry automático.</p>
 */
public final class PublicationOutboxWorker {

    private final String workerId;

    private final PublicationOutboxQueuePort queue;

    private final PublicationChannelResolver channelResolver;

    private final PublicationAttemptStartPort attemptStartPort;

    private final PublicationAttemptCompletionPort
        attemptCompletionPort;

    private final Clock clock;

    private final PublicationRateLimitPolicy rateLimitPolicy;

    private final PublicationRateLimitReservationPort
        rateLimitReservationPort;

    /**
     * Construtor sem rate limiting.
     *
     * <p>A ausência de rate limiting não remove a barreira STARTED.</p>
     */
    public PublicationOutboxWorker(
        String workerId,
        PublicationOutboxQueuePort queue,
        PublicationChannelResolver channelResolver,
        PublicationAttemptStartPort attemptStartPort,
        PublicationAttemptCompletionPort attemptCompletionPort,
        Clock clock
    ) {

        this(
            workerId,
            queue,
            channelResolver,
            attemptStartPort,
            attemptCompletionPort,
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
        PublicationAttemptStartPort attemptStartPort,
        PublicationAttemptCompletionPort attemptCompletionPort,
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

        this.attemptStartPort =
            Objects.requireNonNull(
                attemptStartPort,
                "attemptStartPort must not be null"
            );

        this.attemptCompletionPort =
            Objects.requireNonNull(
                attemptCompletionPort,
                "attemptCompletionPort must not be null"
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

        /*
         * Rate limit precisa ocorrer ANTES de STARTED.
         *
         * Uma publicação simplesmente adiada ainda não representa uma
         * tentativa física contra o provider.
         */
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

        /*
         * Resolver também ocorre antes de STARTED.
         *
         * Falha de configuração/registry ainda não atravessou a
         * fronteira externa e não deve produzir falsa tentativa.
         */
        PublicationChannel channel =
            Objects.requireNonNull(
                channelResolver.resolve(
                    item.channel()
                ),
                "channelResolver returned null PublicationChannel"
            );

        /*
         * ----------------------------------------------------------
         * DURABLE EXTERNAL-SIDE-EFFECT BARRIER
         * ----------------------------------------------------------
         *
         * A partir do retorno de start(), existe uma PublicationAttempt
         * STARTED commitada no PostgreSQL.
         *
         * Somente depois disso publish() é permitido.
         */
        OffsetDateTime startedAt =
            OffsetDateTime.now(
                clock
            );

        PublicationAttemptHandle attempt =
            Objects.requireNonNull(
                attemptStartPort.start(
                    item.id(),
                    workerId,
                    startedAt
                ),
                "attemptStartPort returned null PublicationAttemptHandle"
            );

        validateAttemptHandle(
            item,
            startedAt,
            attempt
        );

        /*
         * Não existe catch em torno de publish().
         *
         * Se uma RuntimeException acontecer aqui, o STARTED permanece
         * durável. Não inventamos FAILED_TRANSIENT ou FAILED_PERMANENT,
         * pois não sabemos se houve ou não efeito externo.
         */
        PublicationResult publicationResult =
            Objects.requireNonNull(
                channel.publish(
                    item.command()
                ),
                "PublicationChannel returned null PublicationResult"
            );

        OffsetDateTime completedAt =
            OffsetDateTime.now(
                clock
            );

        /*
         * Também não existe compensação automática caso esta etapa
         * falhe.
         *
         * Se o provider já produziu efeito e a conclusão local falhar,
         * a tentativa STARTED continua sendo a evidência durável da
         * ambiguidade.
         */
        PublicationOutboxItem completed =
            Objects.requireNonNull(
                attemptCompletionPort.complete(
                    attempt,
                    workerId,
                    publicationResult,
                    completedAt
                ),
                "attemptCompletionPort returned null PublicationOutboxItem"
            );

        return PublicationOutboxWorkerRunResult
            .completed(
                completed.id(),
                completed.status()
            );
    }

    private void validateAttemptHandle(
        PublicationOutboxItem item,
        OffsetDateTime requestedStartedAt,
        PublicationAttemptHandle attempt
    ) {

        if (attempt.publicationOutboxId()
            != item.id()) {

            throw new IllegalStateException(
                "Started publication attempt belongs to unexpected "
                    + "outbox item"
            );
        }

        if (!attempt.startedAt()
            .toInstant()
            .equals(
                requestedStartedAt.toInstant()
            )) {

            throw new IllegalStateException(
                "Started publication attempt returned unexpected "
                    + "startedAt"
            );
        }
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
