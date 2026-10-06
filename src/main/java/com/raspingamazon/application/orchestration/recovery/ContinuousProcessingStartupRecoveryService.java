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
 *         drenar ProcessingJobs RUNNING abandonados até quiescência;
 *     </li>
 *     <li>
 *         drenar a reconciliação de ProcessingRuns que ainda precisam
 *         da unidade durável PUBLICATION_DISPATCH.
 *     </li>
 * </ol>
 *
 * <p>O coordenador é fail-closed. Qualquer exceção interrompe a
 * sequência imediatamente. O bootstrap que utilizar este serviço
 * não deve iniciar worker ou scheduler quando a recuperação falhar.</p>
 *
 * <p>Nenhuma transação global envolve as três etapas. Cada autoridade
 * durável mantém sua própria unidade transacional.</p>
 *
 * <p>A publication_outbox não utiliza paginação no adapter JDBC de
 * recovery; ProcessingJob e reconciliação usam seus limites
 * configurados como tamanhos de página, nunca como teto total do
 * startup recovery.</p>
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
     * Executa recovery completo de startup até quiescência.
     *
     * <p>Este método é síncrono por design. Somente depois de seu
     * retorno bem-sucedido o runtime deve iniciar worker/scheduler.</p>
     */
    public ContinuousProcessingStartupRecoveryResult recover() {

        PublicationOutboxLeaseRecoveryResult publicationOutboxRecovery =
            publicationOutboxRecoveryService.recoverOnce();

        ProcessingJobLeaseRecoveryResult processingJobRecovery =
            processingJobRecoveryService.recoverUntilQuiescent();

        PublicationDispatchReconciliationResult
            publicationDispatchReconciliation =
                publicationDispatchReconciliationService
                    .reconcileUntilQuiescent(
                        publicationDispatchReconciliationLimit
                    );

        return new ContinuousProcessingStartupRecoveryResult(
            publicationOutboxRecovery,
            processingJobRecovery,
            publicationDispatchReconciliation
        );
    }
}
