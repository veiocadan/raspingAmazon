package com.raspingamazon.application.publication.outbox.worker;

import com.raspingamazon.application.publication.outbox.PublicationOutboxStatus;

import java.util.Objects;

/**
 * Resultado observável de uma única rodada do worker de publicação.
 *
 * <p>Quando nenhuma entrada estava disponível,
 * claimedOutboxId e finalStatus permanecem nulos.</p>
 */
public record PublicationOutboxWorkerRunResult(
    boolean outboxClaimed,
    Long claimedOutboxId,
    PublicationOutboxStatus finalStatus
) {

    public PublicationOutboxWorkerRunResult {

        if (!outboxClaimed) {

            if (claimedOutboxId != null
                || finalStatus != null) {

                throw new IllegalArgumentException(
                    "Unclaimed worker result must not contain "
                        + "outbox data"
                );
            }

        } else {

            if (claimedOutboxId == null
                || claimedOutboxId <= 0L) {

                throw new IllegalArgumentException(
                    "Claimed worker result requires "
                        + "positive outbox id"
                );
            }

            Objects.requireNonNull(
                finalStatus,
                "Claimed worker result requires finalStatus"
            );
        }
    }

    public static PublicationOutboxWorkerRunResult idle() {

        return new PublicationOutboxWorkerRunResult(
            false,
            null,
            null
        );
    }

    public static PublicationOutboxWorkerRunResult completed(
        long outboxId,
        PublicationOutboxStatus finalStatus
    ) {

        return new PublicationOutboxWorkerRunResult(
            true,
            outboxId,
            finalStatus
        );
    }
}
