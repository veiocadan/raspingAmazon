package com.raspingamazon.application.orchestration.recovery;

import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryResult;
import com.raspingamazon.application.publication.PublicationDispatchReconciliationResult;
import com.raspingamazon.application.publication.outbox.recovery.PublicationOutboxLeaseRecoveryResult;

import java.util.Objects;

/**
 * Resultado agregado da recuperação executada antes do início do
 * processamento contínuo.
 *
 * <p>O resultado preserva separadamente as três autoridades duráveis
 * envolvidas no restart:</p>
 *
 * <ul>
 *     <li>publication_outbox;</li>
 *     <li>processing_job;</li>
 *     <li>reconciliação de PUBLICATION_DISPATCH.</li>
 * </ul>
 */
public record ContinuousProcessingStartupRecoveryResult(
    PublicationOutboxLeaseRecoveryResult publicationOutboxRecovery,
    ProcessingJobLeaseRecoveryResult processingJobRecovery,
    PublicationDispatchReconciliationResult publicationDispatchReconciliation
) {

    public ContinuousProcessingStartupRecoveryResult {

        Objects.requireNonNull(
            publicationOutboxRecovery,
            "publicationOutboxRecovery must not be null"
        );

        Objects.requireNonNull(
            processingJobRecovery,
            "processingJobRecovery must not be null"
        );

        Objects.requireNonNull(
            publicationDispatchReconciliation,
            "publicationDispatchReconciliation must not be null"
        );
    }
}
