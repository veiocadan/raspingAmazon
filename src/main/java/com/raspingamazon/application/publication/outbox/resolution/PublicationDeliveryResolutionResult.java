package com.raspingamazon.application.publication.outbox.resolution;

import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;

import java.util.Objects;

/**
 * Resultado auditável de uma decisão de resolução.
 *
 * <p>{@code resultingStatus} representa o estado produzido pela
 * decisão no instante em que ela foi aplicada. Em replay idempotente
 * esse valor continua sendo o estado registrado no evento, mesmo que
 * a outbox já tenha avançado posteriormente.</p>
 */
public record PublicationDeliveryResolutionResult(
    long resolutionEventId,
    String requestKey,
    long publicationOutboxId,
    PublicationDeliveryResolutionDecision decision,
    PublicationOutboxStatus resultingStatus,
    boolean newlyApplied
) {

    public PublicationDeliveryResolutionResult {

        if (resolutionEventId <= 0L) {

            throw new IllegalArgumentException(
                "resolutionEventId must be positive"
            );
        }

        requestKey =
            requireText(
                requestKey,
                "requestKey"
            );

        if (publicationOutboxId <= 0L) {

            throw new IllegalArgumentException(
                "publicationOutboxId must be positive"
            );
        }

        decision =
            Objects.requireNonNull(
                decision,
                "decision must not be null"
            );

        resultingStatus =
            Objects.requireNonNull(
                resultingStatus,
                "resultingStatus must not be null"
            );

        if (resultingStatus
            != decision.resultingStatus()) {

            throw new IllegalArgumentException(
                "resultingStatus must match decision"
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

        String normalized =
            value.trim();

        if (normalized.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return normalized;
    }
}
