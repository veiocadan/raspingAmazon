package com.raspingamazon.application.publication.outbox;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Solicitação mínima para reservar uma Publication selecionada
 * na outbox.
 *
 * <p>Canal, destino, conteúdo, posição e quota não são recebidos
 * do chamador. Eles serão reconstruídos a partir da SelectionRun,
 * SelectionDecision e Publication persistidas.</p>
 */
public record PublicationOutboxEnqueueRequest(
    long publicationId,
    long selectionRunId,
    OffsetDateTime availableAt,
    OffsetDateTime enqueuedAt
) {

    public PublicationOutboxEnqueueRequest {

        if (publicationId <= 0L) {
            throw new IllegalArgumentException(
                "publicationId must be positive"
            );
        }

        if (selectionRunId <= 0L) {
            throw new IllegalArgumentException(
                "selectionRunId must be positive"
            );
        }

        Objects.requireNonNull(
            availableAt,
            "availableAt must not be null"
        );

        Objects.requireNonNull(
            enqueuedAt,
            "enqueuedAt must not be null"
        );
    }
}
