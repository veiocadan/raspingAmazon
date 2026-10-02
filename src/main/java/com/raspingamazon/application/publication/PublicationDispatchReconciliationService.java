package com.raspingamazon.application.publication;

import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;
import com.raspingamazon.application.orchestration.ProcessingJobType;
import com.raspingamazon.application.orchestration.port.ProcessingJobQueuePort;
import com.raspingamazon.application.publication.port.ProcessingRunPublicationReadinessQueryPort;
import com.raspingamazon.application.publication.port.PublicationDispatchReconciliationCandidateQueryPort;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reconcilia ProcessingRuns persistidas com a unidade durável
 * PUBLICATION_DISPATCH.
 *
 * <p>Este serviço não executa publicação.</p>
 *
 * <p>Sua responsabilidade termina na existência idempotente do job
 * que posteriormente executará a publicação.</p>
 *
 * <p>Fluxo:</p>
 *
 * <pre>
 * candidata
 *    |
 *    v
 * readiness
 *    |
 *    +-- IN_PROGRESS
 *    |       -> nenhuma ação
 *    |
 *    +-- READY
 *    |       -> enqueue PUBLICATION_DISPATCH
 *    |
 *    +-- BLOCKED
 *            -> enqueue PUBLICATION_DISPATCH
 * </pre>
 *
 * <p>Runs BLOCKED também recebem job porque precisamos transformar
 * esse estado lógico em uma conclusão operacional auditável da etapa
 * de publicação. O futuro handler será responsável por encerrá-lo
 * como DEAD sem produzir publicação parcial.</p>
 *
 * <p>IN_PROGRESS deliberadamente não produz retry nem incrementa
 * attemptCount de ProcessingJob.</p>
 */
public final class PublicationDispatchReconciliationService {

    private static final String IDEMPOTENCY_PREFIX =
        "publication-dispatch:";

    private final PublicationDispatchReconciliationCandidateQueryPort
        candidateQueryPort;

    private final ProcessingRunPublicationReadinessQueryPort
        readinessQueryPort;

    private final ProcessingJobQueuePort
        jobQueue;

    private final Clock clock;

    private final int publicationDispatchMaxAttempts;

    public PublicationDispatchReconciliationService(
        PublicationDispatchReconciliationCandidateQueryPort candidateQueryPort,
        ProcessingRunPublicationReadinessQueryPort readinessQueryPort,
        ProcessingJobQueuePort jobQueue,
        Clock clock,
        int publicationDispatchMaxAttempts
    ) {

        this.candidateQueryPort =
            Objects.requireNonNull(
                candidateQueryPort,
                "candidateQueryPort must not be null"
            );

        this.readinessQueryPort =
            Objects.requireNonNull(
                readinessQueryPort,
                "readinessQueryPort must not be null"
            );

        this.jobQueue =
            Objects.requireNonNull(
                jobQueue,
                "jobQueue must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );

        if (publicationDispatchMaxAttempts <= 0) {

            throw new IllegalArgumentException(
                "publicationDispatchMaxAttempts must be positive"
            );
        }

        this.publicationDispatchMaxAttempts =
            publicationDispatchMaxAttempts;
    }

    public PublicationDispatchReconciliationResult reconcile(
        int limit
    ) {

        if (limit <= 0) {

            throw new IllegalArgumentException(
                "limit must be positive"
            );
        }

        List<Long> loadedCandidates =
            Objects.requireNonNull(
                candidateQueryPort.findCandidates(
                    limit
                ),
                "candidateQueryPort returned null"
            );

        if (loadedCandidates.size() > limit) {

            throw new IllegalStateException(
                "candidateQueryPort returned more runs than requested"
            );
        }

        /*
         * Evita executar duas vezes a mesma identidade caso uma
         * implementação defeituosa da porta produza duplicatas.
         *
         * A constraint da fila continuaria protegendo a persistência,
         * mas a fronteira de aplicação deve permanecer determinística.
         */
        Set<Long> seenProcessingRunIds =
            new HashSet<>();

        int readyCount = 0;
        int inProgressCount = 0;
        int blockedCount = 0;
        int enqueuedCount = 0;

        OffsetDateTime availableAt =
            OffsetDateTime.now(
                clock
            );

        for (Long processingRunId
            : loadedCandidates) {

            if (processingRunId == null
                || processingRunId <= 0L) {

                throw new IllegalStateException(
                    "candidateQueryPort returned invalid "
                        + "processingRunId"
                );
            }

            if (!seenProcessingRunIds.add(
                processingRunId
            )) {

                throw new IllegalStateException(
                    "candidateQueryPort returned duplicate "
                        + "processingRunId "
                        + processingRunId
                );
            }

            ProcessingRunPublicationReadiness readiness =
                readinessQueryPort
                    .findByProcessingRunId(
                        processingRunId
                    )
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "ProcessingRun disappeared during "
                                    + "publication dispatch reconciliation: "
                                    + processingRunId
                            )
                    );

            switch (readiness.status()) {

                case IN_PROGRESS ->
                    inProgressCount++;

                case READY -> {

                    readyCount++;

                    enqueue(
                        processingRunId,
                        availableAt
                    );

                    enqueuedCount++;
                }

                case BLOCKED -> {

                    blockedCount++;

                    enqueue(
                        processingRunId,
                        availableAt
                    );

                    enqueuedCount++;
                }
            }
        }

        return new PublicationDispatchReconciliationResult(
            loadedCandidates.size(),
            readyCount,
            inProgressCount,
            blockedCount,
            enqueuedCount
        );
    }

    private void enqueue(
        long processingRunId,
        OffsetDateTime availableAt
    ) {

        ProcessingJob job =
            Objects.requireNonNull(
                jobQueue.enqueue(
                    ProcessingJobSubmission.publicationDispatch(
                        processingRunId,
                        idempotencyKey(
                            processingRunId
                        ),
                        publicationDispatchMaxAttempts,
                        availableAt
                    )
                ),
                "jobQueue returned null"
            );

        validateEnqueuedJob(
            processingRunId,
            job
        );
    }

    private void validateEnqueuedJob(
        long expectedProcessingRunId,
        ProcessingJob job
    ) {

        if (job.type()
            != ProcessingJobType.PUBLICATION_DISPATCH) {

            throw new IllegalStateException(
                "jobQueue returned unexpected job type "
                    + job.type()
            );
        }

        if (!Objects.equals(
            job.processingRunId(),
            expectedProcessingRunId
        )) {

            throw new IllegalStateException(
                "jobQueue returned PUBLICATION_DISPATCH "
                    + "for unexpected ProcessingRun"
            );
        }

        String expectedIdempotencyKey =
            idempotencyKey(
                expectedProcessingRunId
            );

        if (!expectedIdempotencyKey.equals(
            job.idempotencyKey()
        )) {

            throw new IllegalStateException(
                "jobQueue returned unexpected idempotencyKey"
            );
        }
    }

    private String idempotencyKey(
        long processingRunId
    ) {

        return IDEMPOTENCY_PREFIX
            + processingRunId;
    }
}
