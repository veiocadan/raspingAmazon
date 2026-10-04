package com.raspingamazon.application.orchestration.recovery;

import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryResult;
import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryService;
import com.raspingamazon.application.publication.PublicationDispatchReconciliationResult;
import com.raspingamazon.application.publication.PublicationDispatchReconciliationService;
import com.raspingamazon.application.publication.outbox.recovery.PublicationOutboxLeaseRecoveryResult;
import com.raspingamazon.application.publication.outbox.recovery.PublicationOutboxLeaseRecoveryService;

import java.util.Objects;

/**
 * Coordena a recuperação síncrona que deve ocorrer antes de os loops
 * normais do processamento contínuo serem iniciados.
 *
 * <p>A ordem é deliberada:</p>
 *
 * <ol>
 *     <li>
 *         recuperar publication_outbox, porque ela representa a
 *         fronteira com efeitos externos e pode produzir
 *         DELIVERY_UNKNOWN;
 *     </li>
 *     <li>
 *         recuperar ProcessingJobs RUNNING abandonados;
 *     </li>
 *     <li>
 *         reconciliar ProcessingRuns que ainda precisam da unidade
 *         durável PUBLICATION_DISPATCH.
 *     </li>
 * </ol>
 *
 * <p>O coordenador é fail-closed. Qualquer exceção interrompe a
 * sequência imediatamente. O bootstrap que utilizar este serviço
 * não deve iniciar worker ou scheduler quando a recuperação falhar.</p>
 *
 * <p>Nenhuma transação global envolve as três etapas. Cada autoridade
 * durável mantém sua própria unidade transacional.</p>
 */
public final class ContinuousProcessingStartupRecoveryService {

    private final PublicationOutboxLeaseRecoveryService
        publicationOutboxRecoveryService;

    private final ProcessingJobLeaseRecoveryService
        processingJobRecoveryService;

    private final PublicationDispatchReconciliationService
        publicationDispatchReconciliationService;

    private final int publicationDispatchReconciliationLimit;

    public ContinuousProcessingStartupRecoveryService(
        PublicationOutboxLeaseRecoveryService publicationOutboxRecoveryService,
        ProcessingJobLeaseRecoveryService processingJobRecoveryService,
        PublicationDispatchReconciliationService publicationDispatchReconciliationService,
        int publicationDispatchReconciliationLimit
    ) {

        this.publicationOutboxRecoveryService =
            Objects.requireNonNull(
                publicationOutboxRecoveryService,
                "publicationOutboxRecoveryService must not be null"
            );

        this.processingJobRecoveryService =
            Objects.requireNonNull(
                processingJobRecoveryService,
                "processingJobRecoveryService must not be null"
            );

        this.publicationDispatchReconciliationService =
            Objects.requireNonNull(
                publicationDispatchReconciliationService,
                "publicationDispatchReconciliationService must not be null"
            );

        if (publicationDispatchReconciliationLimit <= 0) {

            throw new IllegalArgumentException(
                "publicationDispatchReconciliationLimit must be positive"
            );
        }

        this.publicationDispatchReconciliationLimit =
            publicationDispatchReconciliationLimit;
    }

    /**
     * Executa uma rodada completa de recovery de startup.
     *
     * <p>Este método é síncrono por design. Somente depois de seu
     * retorno bem-sucedido o runtime deve iniciar worker/scheduler.</p>
     */
    public ContinuousProcessingStartupRecoveryResult recover() {

        PublicationOutboxLeaseRecoveryResult publicationOutboxRecovery =
            publicationOutboxRecoveryService.recoverOnce();

        ProcessingJobLeaseRecoveryResult processingJobRecovery =
            processingJobRecoveryService.recoverOnce();

        PublicationDispatchReconciliationResult
            publicationDispatchReconciliation =
                publicationDispatchReconciliationService.reconcile(
                    publicationDispatchReconciliationLimit
                );

        return new ContinuousProcessingStartupRecoveryResult(
            publicationOutboxRecovery,
            processingJobRecovery,
            publicationDispatchReconciliation
        );
    }
}
