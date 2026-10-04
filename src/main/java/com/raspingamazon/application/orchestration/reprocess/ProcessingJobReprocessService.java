package com.raspingamazon.application.orchestration.reprocess;

import com.raspingamazon.application.orchestration.reprocess.port.ProcessingJobReprocessPort;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Caso de uso de reprocessamento explícito de ProcessingJob.
 *
 * <p>O serviço não conhece SQL, interface administrativa ou worker.
 * Ele somente fixa a fotografia temporal da decisão e delega a
 * transição auditável à porta.</p>
 */
public final class ProcessingJobReprocessService {

    private final ProcessingJobReprocessPort reprocessPort;

    private final Clock clock;

    public ProcessingJobReprocessService(
        ProcessingJobReprocessPort reprocessPort,
        Clock clock
    ) {

        this.reprocessPort =
            Objects.requireNonNull(
                reprocessPort,
                "reprocessPort must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    public ProcessingJobReprocessResult reprocess(
        ProcessingJobReprocessRequest request
    ) {

        ProcessingJobReprocessRequest validatedRequest =
            Objects.requireNonNull(
                request,
                "request must not be null"
            );

        OffsetDateTime requestedAt =
            OffsetDateTime.now(
                clock
            );

        return Objects.requireNonNull(
            reprocessPort.reprocess(
                validatedRequest,
                requestedAt
            ),
            "reprocessPort returned null"
        );
    }
}
