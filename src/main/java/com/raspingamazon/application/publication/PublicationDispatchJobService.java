package com.raspingamazon.application.publication;

import com.raspingamazon.application.publication.port.ProcessingRunPublicationReadinessQueryPort;

import java.util.Objects;

/**
 * Handler de aplicação da unidade durável PUBLICATION_DISPATCH.
 *
 * <p>Este serviço é deliberadamente pequeno:</p>
 *
 * <pre>
 * ProcessingRun
 *      ↓
 * revalidar readiness
 *      │
 *      ├─ READY
 *      │    ↓
 *      │  PublicationProcessingRunDispatchUseCase
 *      │
 *      ├─ BLOCKED
 *      │    ↓
 *      │  falha permanente explícita
 *      │
 *      └─ IN_PROGRESS
 *           ↓
 *         violação de invariância
 * </pre>
 *
 * <p>A revalidação é obrigatória porque existe intervalo temporal
 * entre:</p>
 *
 * <pre>
 * reconciliador cria o ProcessingJob
 *          ↓
 * worker reivindica o ProcessingJob
 * </pre>
 *
 * <p>O handler não aprova publicações e não contém regra comercial.</p>
 */
public final class PublicationDispatchJobService {

    private final ProcessingRunPublicationReadinessQueryPort
        readinessQueryPort;

    private final PublicationProcessingRunDispatchUseCase
        dispatchUseCase;

    public PublicationDispatchJobService(
        ProcessingRunPublicationReadinessQueryPort readinessQueryPort,
        PublicationProcessingRunDispatchUseCase dispatchUseCase
    ) {

        this.readinessQueryPort =
            Objects.requireNonNull(
                readinessQueryPort,
                "readinessQueryPort must not be null"
            );

        this.dispatchUseCase =
            Objects.requireNonNull(
                dispatchUseCase,
                "dispatchUseCase must not be null"
            );
    }

    /**
     * Executa o PUBLICATION_DISPATCH de uma ProcessingRun.
     *
     * @param processingRunId identidade persistida da run
     * @return resultado do pipeline automático
     */
    public PublicationSelectionDispatchResult execute(
        long processingRunId
    ) {

        if (processingRunId <= 0L) {

            throw new IllegalArgumentException(
                "processingRunId must be positive"
            );
        }

        ProcessingRunPublicationReadiness readiness =
            readinessQueryPort
                .findByProcessingRunId(
                    processingRunId
                )
                .orElseThrow(
                    () ->
                        PublicationDispatchJobException
                            .processingRunNotFound(
                                processingRunId
                            )
                );

        validateReadinessIdentity(
            processingRunId,
            readiness
        );

        return switch (readiness.status()) {

            case READY ->
                Objects.requireNonNull(
                    dispatchUseCase.process(
                        processingRunId
                    ),
                    "dispatchUseCase returned null"
                );

            case BLOCKED ->
                throw PublicationDispatchJobException
                    .blocked(
                        readiness
                    );

            case IN_PROGRESS ->
                throw PublicationDispatchJobException
                    .notReady(
                        readiness
                    );
        };
    }

    private void validateReadinessIdentity(
        long expectedProcessingRunId,
        ProcessingRunPublicationReadiness readiness
    ) {

        Objects.requireNonNull(
            readiness,
            "readiness must not be null"
        );

        if (readiness.processingRunId()
            != expectedProcessingRunId) {

            throw PublicationDispatchJobException
                .readinessIdentityMismatch(
                    expectedProcessingRunId,
                    readiness.processingRunId()
                );
        }
    }
}
