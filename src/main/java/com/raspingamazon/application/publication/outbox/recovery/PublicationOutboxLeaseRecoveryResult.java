package com.raspingamazon.application.publication.outbox.recovery;

/**
 * Resultado observável de uma rodada de recuperação de leases
 * expirados da publication_outbox.
 *
 * @param recoveredLeaseCount quantidade total de outboxes recuperadas
 */
public record PublicationOutboxLeaseRecoveryResult(
    int recoveredLeaseCount
) {

    public PublicationOutboxLeaseRecoveryResult {

        if (recoveredLeaseCount < 0) {

            throw new IllegalArgumentException(
                "recoveredLeaseCount must not be negative"
            );
        }
    }
}
