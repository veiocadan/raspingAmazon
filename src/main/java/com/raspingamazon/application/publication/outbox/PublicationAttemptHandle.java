package com.raspingamazon.application.publication.outbox;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Identidade durável de uma tentativa física de publicação que já
 * atravessou a barreira local de persistência.
 *
 * <p>A existência deste handle significa que:</p>
 *
 * <ol>
 *     <li>a outbox estava PROCESSING;</li>
 *     <li>o worker era proprietário do lease;</li>
 *     <li>PublicationAttempt STARTED foi persistido;</li>
 *     <li>a transação responsável pelo STARTED terminou com commit.</li>
 * </ol>
 *
 * <p>O handle ainda não significa que o provider recebeu a requisição.
 * Ele apenas estabelece que, a partir deste ponto, uma queda do processo
 * precisa ser tratada como possível resultado externo desconhecido.</p>
 */
public record PublicationAttemptHandle(
    long id,
    long publicationOutboxId,
    int attemptNumber,
    OffsetDateTime startedAt
) {

    public PublicationAttemptHandle {

        requirePositive(
            id,
            "id"
        );

        requirePositive(
            publicationOutboxId,
            "publicationOutboxId"
        );

        if (attemptNumber <= 0) {

            throw new IllegalArgumentException(
                "attemptNumber must be positive"
            );
        }

        Objects.requireNonNull(
            startedAt,
            "startedAt must not be null"
        );
    }

    private static void requirePositive(
        long value,
        String fieldName
    ) {

        if (value <= 0L) {

            throw new IllegalArgumentException(
                fieldName + " must be positive"
            );
        }
    }
}
