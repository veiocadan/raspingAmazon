package com.raspingamazon.application.publication;

import java.util.Objects;

/**
 * Falha funcional específica da execução de PUBLICATION_DISPATCH.
 *
 * <p>Essas condições são deliberadamente separadas de
 * IllegalStateException e IllegalArgumentException para que a
 * infraestrutura de jobs preserve códigos operacionais explícitos.</p>
 */
public final class PublicationDispatchJobException
    extends RuntimeException {

    public enum Reason {

        /**
         * O job referencia uma ProcessingRun que deixou de existir
         * ou nunca existiu.
         */
        PROCESSING_RUN_NOT_FOUND(
            "PUBLICATION_DISPATCH_RUN_NOT_FOUND"
        ),

        /**
         * A run existe, mas sua linhagem persistente contém condição
         * terminal que impede publicação completa.
         */
        PROCESSING_RUN_BLOCKED(
            "PUBLICATION_DISPATCH_RUN_BLOCKED"
        ),

        /**
         * Um PUBLICATION_DISPATCH foi executado enquanto sua run
         * voltou a aparecer como IN_PROGRESS.
         *
         * <p>Isso viola a fronteira esperada entre reconciliador e
         * handler.</p>
         */
        NOT_READY_INVARIANT(
            "PUBLICATION_DISPATCH_NOT_READY_INVARIANT"
        );

        private final String errorCode;

        Reason(
            String errorCode
        ) {

            this.errorCode =
                Objects.requireNonNull(
                    errorCode,
                    "errorCode must not be null"
                );
        }

        public String errorCode() {

            return errorCode;
        }
    }

    private final long processingRunId;

    private final Reason reason;

    private PublicationDispatchJobException(
        long processingRunId,
        Reason reason,
        String message
    ) {

        super(
            requireText(
                message,
                "message must not be blank"
            )
        );

        if (processingRunId <= 0L) {

            throw new IllegalArgumentException(
                "processingRunId must be positive"
            );
        }

        this.processingRunId =
            processingRunId;

        this.reason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            );
    }

    public static PublicationDispatchJobException
    processingRunNotFound(
        long processingRunId
    ) {

        return new PublicationDispatchJobException(
            processingRunId,
            Reason.PROCESSING_RUN_NOT_FOUND,
            "ProcessingRun "
                + processingRunId
                + " was not found while executing "
                + "PUBLICATION_DISPATCH"
        );
    }

    public static PublicationDispatchJobException blocked(
        ProcessingRunPublicationReadiness readiness
    ) {

        Objects.requireNonNull(
            readiness,
            "readiness must not be null"
        );

        return new PublicationDispatchJobException(
            readiness.processingRunId(),
            Reason.PROCESSING_RUN_BLOCKED,
            "ProcessingRun "
                + readiness.processingRunId()
                + " is blocked for PUBLICATION_DISPATCH"
                + readinessDescription(
                readiness
            )
        );
    }

    public static PublicationDispatchJobException notReady(
        ProcessingRunPublicationReadiness readiness
    ) {

        Objects.requireNonNull(
            readiness,
            "readiness must not be null"
        );

        return new PublicationDispatchJobException(
            readiness.processingRunId(),
            Reason.NOT_READY_INVARIANT,
            "ProcessingRun "
                + readiness.processingRunId()
                + " is still IN_PROGRESS while executing "
                + "PUBLICATION_DISPATCH"
                + readinessDescription(
                readiness
            )
        );
    }

    public static PublicationDispatchJobException
    readinessIdentityMismatch(
        long expectedProcessingRunId,
        long actualProcessingRunId
    ) {

        return new PublicationDispatchJobException(
            expectedProcessingRunId,
            Reason.NOT_READY_INVARIANT,
            "Publication readiness identity mismatch: expected "
                + expectedProcessingRunId
                + " but received "
                + actualProcessingRunId
        );
    }

    public long processingRunId() {

        return processingRunId;
    }

    public Reason reason() {

        return reason;
    }

    public String errorCode() {

        return reason.errorCode();
    }

    private static String readinessDescription(
        ProcessingRunPublicationReadiness readiness
    ) {

        return " [processingRunStatus="
            + readiness.processingRunStatus()
            + ", totalCandidates="
            + readiness.totalCandidates()
            + ", completedCandidates="
            + readiness.completedCandidates()
            + ", inProgressCandidates="
            + readiness.inProgressCandidates()
            + ", blockedCandidates="
            + readiness.blockedCandidates()
            + "]";
    }

    private static String requireText(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
