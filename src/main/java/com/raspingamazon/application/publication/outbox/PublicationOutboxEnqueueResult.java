package com.raspingamazon.application.publication.outbox;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Resultado estruturado da tentativa de enqueue.
 */
public record PublicationOutboxEnqueueResult(
    PublicationOutboxEnqueueStatus status,
    Long outboxId
) {

    public PublicationOutboxEnqueueResult {

        Objects.requireNonNull(
            status,
            "status must not be null"
        );

        boolean requiresOutboxId =
            status
                == PublicationOutboxEnqueueStatus.ENQUEUED
                || status
                == PublicationOutboxEnqueueStatus.ALREADY_ENQUEUED;

        if (requiresOutboxId) {

            if (outboxId == null || outboxId <= 0L) {
                throw new IllegalArgumentException(
                    status
                        + " result must contain a positive outboxId"
                );
            }

        } else if (outboxId != null) {

            throw new IllegalArgumentException(
                status
                    + " result must not contain outboxId"
            );
        }
    }

    public static PublicationOutboxEnqueueResult enqueued(
        long outboxId
    ) {

        return new PublicationOutboxEnqueueResult(
            PublicationOutboxEnqueueStatus.ENQUEUED,
            outboxId
        );
    }

    public static PublicationOutboxEnqueueResult alreadyEnqueued(
        long outboxId
    ) {

        return new PublicationOutboxEnqueueResult(
            PublicationOutboxEnqueueStatus.ALREADY_ENQUEUED,
            outboxId
        );
    }

    public static PublicationOutboxEnqueueResult quotaExhaustedResult() {

        return new PublicationOutboxEnqueueResult(
            PublicationOutboxEnqueueStatus.QUOTA_EXHAUSTED,
            null
        );
    }

    public static PublicationOutboxEnqueueResult staleSelectionResult() {

        return new PublicationOutboxEnqueueResult(
            PublicationOutboxEnqueueStatus.STALE_SELECTION,
            null
        );
    }

    public boolean enqueued() {

        return status
            == PublicationOutboxEnqueueStatus.ENQUEUED;
    }

    public boolean alreadyEnqueued() {

        return status
            == PublicationOutboxEnqueueStatus.ALREADY_ENQUEUED;
    }

    public boolean quotaExhausted() {

        return status
            == PublicationOutboxEnqueueStatus.QUOTA_EXHAUSTED;
    }

    public boolean staleSelection() {

        return status
            == PublicationOutboxEnqueueStatus.STALE_SELECTION;
    }

    public OptionalLong outboxIdValue() {

        if (outboxId == null) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(
            outboxId
        );
    }
}
