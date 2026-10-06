package com.raspingamazon.infrastructure.composition;

import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryPort;
import com.raspingamazon.application.orchestration.lease.ProcessingJobLeaseRecoveryService;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.orchestration.recovery.ContinuousProcessingStartupRecoveryService;
import com.raspingamazon.application.publication.PublicationDispatchJobService;
import com.raspingamazon.application.publication.PublicationDispatchReconciliationService;
import com.raspingamazon.application.publication.PublicationProcessingRunDispatchService;
import com.raspingamazon.application.publication.outbox.port.PublicationOutboxQueuePort;
import com.raspingamazon.application.publication.outbox.recovery.PublicationOutboxLeaseRecoveryService;
import com.raspingamazon.application.publication.port.ProcessingRunPublicationReadinessQueryPort;
import com.raspingamazon.application.publication.port.PublicationDispatchReconciliationCandidateQueryPort;
import com.raspingamazon.infrastructure.config.ContinuousProcessingRecoveryConfig;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingJobLeaseRecoveryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingJobQueueAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcProcessingRunPublicationReadinessQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationDispatchReconciliationCandidateQueryAdapter;
import com.raspingamazon.infrastructure.persistence.adapter.JdbcPublicationOutboxQueueAdapter;

import java.sql.Connection;
import java.time.Clock;
import java.util.Objects;
import java.util.function.LongConsumer;

/**
 * Composition root das responsabilidades de resiliência necessárias
 * antes do runtime contínuo ser iniciado.
 *
 * <p>Esta composition reúne somente componentes já existentes:</p>
 *
 * <ul>
 *     <li>handler durável PUBLICATION_DISPATCH;</li>
 *     <li>recovery de ProcessingJob;</li>
 *     <li>recovery de publication_outbox;</li>
 *     <li>reconciliação de PUBLICATION_DISPATCH.</li>
 * </ul>
 *
 * <p>Ela não cria threads e não inicia o runtime.</p>
 */
public final class ContinuousProcessingRecoveryComposition {

    private ContinuousProcessingRecoveryComposition() {
    }

    public static Components create(
        Connection workerConnection,
        ContinuousProcessingRecoveryConfig config,
        Clock clock
    ) {

        Connection validatedConnection =
            Objects.requireNonNull(
                workerConnection,
                "workerConnection must not be null"
            );

        Objects.requireNonNull(
            config,
            "config must not be null"
        );

        Objects.requireNonNull(
            clock,
            "clock must not be null"
        );

        ProcessingRunPublicationReadinessQueryPort readinessQuery =
            new JdbcProcessingRunPublicationReadinessQueryAdapter(
                validatedConnection
            );

        PublicationProcessingRunDispatchService
            publicationDispatchService =
                PublicationAutomationComposition.create(
                    validatedConnection
                );

        PublicationDispatchJobService publicationDispatchJobService =
            new PublicationDispatchJobService(
                readinessQuery,
                publicationDispatchService::process
            );

        return create(
            config,
            clock,
            publicationDispatchJobService::execute,
            new JdbcProcessingJobLeaseRecoveryAdapter(
                validatedConnection
            ),
            new JdbcPublicationOutboxQueueAdapter(
                validatedConnection
            ),
            new JdbcPublicationDispatchReconciliationCandidateQueryAdapter(
                validatedConnection
            ),
            readinessQuery,
            new JdbcProcessingJobQueueAdapter(
                validatedConnection
            )
        );
    }

    static Components create(
        ContinuousProcessingRecoveryConfig config,
        Clock clock,
        LongConsumer publicationDispatchHandler,
        ProcessingJobLeaseRecoveryPort processingJobRecoveryPort,
        PublicationOutboxQueuePort publicationOutboxQueue,
        PublicationDispatchReconciliationCandidateQueryPort
            reconciliationCandidateQuery,
        ProcessingRunPublicationReadinessQueryPort readinessQuery,
        ProcessingJobQueuePort processingJobQueue
    ) {

        ContinuousProcessingRecoveryConfig validatedConfig =
            Objects.requireNonNull(
                config,
                "config must not be null"
            );

        Clock validatedClock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        LongConsumer validatedPublicationDispatchHandler =
            Objects.requireNonNull(
                publicationDispatchHandler,
                "publicationDispatchHandler must not be null"
            );

        ProcessingJobLeaseRecoveryService processingJobRecoveryService =
            new ProcessingJobLeaseRecoveryService(
                Objects.requireNonNull(
                    processingJobRecoveryPort,
                    "processingJobRecoveryPort must not be null"
                ),
                validatedConfig.processingJobLeaseDuration(),
                validatedConfig.processingJobRecoveryBatchSize(),
                validatedClock
            );

        PublicationOutboxLeaseRecoveryService
            publicationOutboxRecoveryService =
                new PublicationOutboxLeaseRecoveryService(
                    Objects.requireNonNull(
                        publicationOutboxQueue,
                        "publicationOutboxQueue must not be null"
                    ),
                    validatedConfig.publicationOutboxLeaseDuration(),
                    validatedClock
                );

        PublicationDispatchReconciliationService
            publicationDispatchReconciliationService =
                new PublicationDispatchReconciliationService(
                    Objects.requireNonNull(
                        reconciliationCandidateQuery,
                        "reconciliationCandidateQuery must not be null"
                    ),
                    Objects.requireNonNull(
                        readinessQuery,
                        "readinessQuery must not be null"
                    ),
                    Objects.requireNonNull(
                        processingJobQueue,
                        "processingJobQueue must not be null"
                    ),
                    validatedClock,
                    validatedConfig.publicationDispatchMaxAttempts()
                );

        ContinuousProcessingStartupRecoveryService startupRecoveryService =
            new ContinuousProcessingStartupRecoveryService(
                publicationOutboxRecoveryService,
                processingJobRecoveryService,
                publicationDispatchReconciliationService,
                validatedConfig.publicationDispatchReconciliationLimit()
            );

        return new Components(
            validatedPublicationDispatchHandler,
            startupRecoveryService
        );
    }

    /**
     * Peças produzidas para o bootstrap.
     *
     * <p>O mesmo handler entregue ao ProcessingWorker corresponde ao
     * job que o reconciliador cria durante o recovery.</p>
     */
    public record Components(
        LongConsumer publicationDispatchHandler,
        ContinuousProcessingStartupRecoveryService startupRecoveryService
    ) {

        public Components {

            Objects.requireNonNull(
                publicationDispatchHandler,
                "publicationDispatchHandler must not be null"
            );

            Objects.requireNonNull(
                startupRecoveryService,
                "startupRecoveryService must not be null"
            );
        }
    }
}
