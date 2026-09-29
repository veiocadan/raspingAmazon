package com.raspingamazon.application.publication.outbox.worker;

import com.raspingamazon.application.publication.channel.PublicationChannel;
import com.raspingamazon.application.publication.channel.PublicationChannelResolver;
import com.raspingamazon.application.publication.channel.PublicationResult;
import com.raspingamazon.application.publication.outbox.PublicationOutboxItem;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxCompletionPort;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxQueuePort;

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
 *     <li>backoff;</li>
 *     <li>rate limit;</li>
 *     <li>Telegram;</li>
 *     <li>WhatsApp.</li>
 * </ul>
 *
 * <p>A composição operacional decide quando executar runOnce().</p>
 *
 * <p>Falhas funcionais conhecidas pelo canal devem ser devolvidas
 * como PublicationResult. Uma RuntimeException inesperada do
 * resolver ou do adapter não é reclassificada artificialmente
 * pelo worker. Nesse caso o lease permanece PROCESSING e poderá
 * ser recuperado pela política de lease da outbox.</p>
 */
public final class PublicationOutboxWorker {

    private final String workerId;

    private final PublicationOutboxQueuePort queue;

    private final PublicationChannelResolver channelResolver;

    private final PublicationOutboxCompletionPort completionPort;

    private final Clock clock;

    public PublicationOutboxWorker(
        String workerId,
        PublicationOutboxQueuePort queue,
        PublicationChannelResolver channelResolver,
        PublicationOutboxCompletionPort completionPort,
        Clock clock
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
