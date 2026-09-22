package com.raspingamazon.application.orchestration.lease;

import java.time.OffsetDateTime;

/**
 * Porta responsável pela recuperação de ProcessingJobs abandonados
 * por workers que deixaram de executar.
 *
 * <p>Um job é considerado abandonado quando permanece RUNNING com
 * lockedAt anterior ou igual ao limite de expiração informado.</p>
 */
public interface ProcessingJobLeaseRecoveryPort {

    /**
     * Recupera um lote de leases expirados.
     *
     * <p>Jobs que ainda possuem tentativas são movidos para RETRY_WAIT.
     * Jobs que já consumiram maxAttempts são movidos para DEAD.</p>
     *
     * @param leaseExpiredBefore limite máximo de lockedAt considerado expirado
     * @param recoveredAt instante da recuperação
     * @param batchSize quantidade máxima de jobs recuperados na rodada
     * @return quantidade de jobs recuperados por estado de destino
     */
    ProcessingJobLeaseRecoveryResult recoverExpiredLeases(
        OffsetDateTime leaseExpiredBefore,
        OffsetDateTime recoveredAt,
        int batchSize
    );
}
