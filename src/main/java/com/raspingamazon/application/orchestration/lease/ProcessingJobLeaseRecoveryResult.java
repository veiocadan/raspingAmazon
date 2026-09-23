package com.raspingamazon.application.orchestration.lease;

/**
 * Resultado agregado de uma rodada de recuperação de leases expirados.
 *
 * @param retryWaitCount jobs devolvidos para RETRY_WAIT
 * @param deadCount jobs encerrados em DEAD por esgotamento das tentativas
 */
public record ProcessingJobLeaseRecoveryResult(
    int retryWaitCount,
    int deadCount
) {

    public ProcessingJobLeaseRecoveryResult {

        if (retryWaitCount < 0) {
            throw new IllegalArgumentException(
                "retryWaitCount must not be negative"
            );
        }

        if (deadCount < 0) {
            throw new IllegalArgumentException(
                "deadCount must not be negative"
            );
        }
    }

    public int totalRecovered() {

        return retryWaitCount
            + deadCount;
    }
}
