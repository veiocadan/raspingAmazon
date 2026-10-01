package com.raspingamazon.application.publication.outbox;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Solicitação para criar uma entrega derivada a partir de uma
 * entrada primária já existente na publication_outbox.
 *
 * <p>O chamador informa somente:</p>
 *
 * <ul>
 *     <li>a outbox primária de origem;</li>
 *     <li>o canal derivado;</li>
 *     <li>o destino derivado;</li>
 *     <li>o instante do enqueue.</li>
 * </ul>
 *
 * <p>Publication, SelectionRun, posição, conteúdo e availableAt
 * NÃO são recebidos do chamador. Eles serão copiados da entrada
 * primária persistida.</p>
 *
 * <p>Isso impede que uma entrega derivada altere os fatos ou a
 * identidade da seleção que a originou.</p>
 */
public record PublicationOutboxDerivedEnqueueRequest(
    long sourceOutboxId,
    String channel,
    String destination,
    OffsetDateTime enqueuedAt
) {

    public PublicationOutboxDerivedEnqueueRequest {

        if (sourceOutboxId <= 0L) {

            throw new IllegalArgumentException(
                "sourceOutboxId must be positive"
            );
        }

        channel =
            requireText(
                channel,
                "channel"
            );

        destination =
            requireText(
                destination,
                "destination"
            );

        Objects.requireNonNull(
            enqueuedAt,
            "enqueuedAt must not be null"
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
