package com.raspingamazon.application.publication.outbox;

import java.util.Objects;

/**
 * Destino lógico de uma entrega derivada da publication_outbox.
 *
 * <p>O target define somente:</p>
 *
 * <ul>
 *     <li>channel;</li>
 *     <li>destination.</li>
 * </ul>
 *
 * <p>Publication, SelectionRun, posição, conteúdo, disponibilidade
 * e quota continuam pertencendo à entrega primária e ao mecanismo
 * persistente de fan-out.</p>
 */
public record PublicationOutboxDerivedTarget(
    String channel,
    String destination
) {

    public PublicationOutboxDerivedTarget {

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
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return trimmed;
    }
}
