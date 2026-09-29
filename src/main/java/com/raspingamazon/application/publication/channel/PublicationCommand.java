package com.raspingamazon.application.publication.channel;

import java.util.Objects;

/**
 * Comando autocontido para publicação externa.
 *
 * <p>O canal recebe tudo que precisa para realizar a entrega.
 * Ele não deve consultar OfferSnapshot, DealEvaluation,
 * PublicationRepository ou qualquer outro repository interno
 * para construir a mensagem.</p>
 *
 * <p>O conteúdo já atravessou geração e aprovação antes de chegar
 * a este contrato.</p>
 */
public record PublicationCommand(
    long publicationId,
    String channel,
    String destination,
    String content
) {

    public PublicationCommand {

        if (publicationId <= 0L) {

            throw new IllegalArgumentException(
                "publicationId must be positive"
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

        content =
            requireText(
                content,
                "content"
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
