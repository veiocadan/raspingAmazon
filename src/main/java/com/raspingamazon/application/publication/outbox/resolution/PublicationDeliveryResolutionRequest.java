package com.raspingamazon.application.publication.outbox.resolution;

import java.util.Objects;

/**
 * Decisão humana/operacional sobre uma entrega ambígua.
 *
 * <p>{@code requestKey} torna a decisão idempotente. A mesma decisão
 * pode ser reenviada com segurança quando o cliente não souber se a
 * primeira chamada foi persistida.</p>
 */
public record PublicationDeliveryResolutionRequest(
    long publicationOutboxId,
    String requestKey,
    PublicationDeliveryResolutionDecision decision,
    String decidedBy,
    String evidence,
    String providerReference
) {

    public PublicationDeliveryResolutionRequest {

        if (publicationOutboxId <= 0L) {

            throw new IllegalArgumentException(
                "publicationOutboxId must be positive"
            );
        }

        requestKey =
            requireText(
                requestKey,
                "requestKey"
            );

        decision =
            Objects.requireNonNull(
                decision,
                "decision must not be null"
            );

        decidedBy =
            requireText(
                decidedBy,
                "decidedBy"
            );

        evidence =
            requireText(
                evidence,
                "evidence"
            );

        providerReference =
            normalizeNullableText(
                providerReference
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

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return normalized;
    }

    private static String normalizeNullableText(
        String value
    ) {

        if (value == null) {
            return null;
        }

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized;
    }
}
