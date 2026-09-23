package com.raspingamazon.application.orchestration.worker;

import com.raspingamazon.application.orchestration.ProcessingJobStatus;

import java.util.Objects;

/**
 * Resultado observável de uma única rodada de processamento.
 *
 * <p>Quando nenhum job estava disponível, claimedJobId e finalStatus
 * permanecem nulos.</p>
 */
public record ProcessingWorkerRunResult(
    boolean jobClaimed,
    Long claimedJobId,
    ProcessingJobStatus finalStatus
) {

    public ProcessingWorkerRunResult {

        if (!jobClaimed) {

            if (claimedJobId != null
                || finalStatus != null) {

                throw new IllegalArgumentException(
                    "Unclaimed worker result must not contain job data"
                );
            }

        } else {

            if (claimedJobId == null
                || claimedJobId <= 0) {

                throw new IllegalArgumentException(
                    "Claimed worker result requires positive job id"
                );
            }

            Objects.requireNonNull(
                finalStatus,
                "Claimed worker result requires finalStatus"
            );
        }
    }

    public static ProcessingWorkerRunResult idle() {

        return new ProcessingWorkerRunResult(
            false,
            null,
            null
        );
    }

    public static ProcessingWorkerRunResult completed(
        long jobId,
        ProcessingJobStatus finalStatus
    ) {

        return new ProcessingWorkerRunResult(
            true,
            jobId,
            finalStatus
        );
    }
}
