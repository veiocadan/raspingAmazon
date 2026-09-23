package com.raspingamazon.application.orchestration.lease;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Caso de uso responsável por executar uma rodada de recuperação
 * de ProcessingJobs cujo lease expirou.
 *
 * <p>A política de expiração pertence à aplicação. O adapter de
 * persistência recebe apenas o limite temporal já calculado.</p>
 */
public final class ProcessingJobLeaseRecoveryService {

    private final ProcessingJobLeaseRecoveryPort
        recoveryPort;

    private final Duration leaseDuration;

    private final int batchSize;

    private final Clock clock;

    public ProcessingJobLeaseRecoveryService(
        ProcessingJobLeaseRecoveryPort recoveryPort,
        Duration leaseDuration,
        int batchSize,
        Clock clock
    ) {

        this.recoveryPort =
            Objects.requireNonNull(
                recoveryPort,
                "recoveryPort must not be null"
            );

        this.leaseDuration =
            requirePositiveDuration(
                leaseDuration
            );

        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                "batchSize must be positive"
            );
        }

        this.batchSize =
            batchSize;

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    /**
     * Executa uma única rodada de recuperação.
     *
     * @return resultado agregado da recuperação
     */
    public ProcessingJobLeaseRecoveryResult recoverOnce() {

        OffsetDateTime recoveredAt =
            OffsetDateTime.now(
                clock
            );

        OffsetDateTime leaseExpiredBefore =
            recoveredAt.minus(
                leaseDuration
            );

        return recoveryPort.recoverExpiredLeases(
            leaseExpiredBefore,
            recoveredAt,
            batchSize
        );
    }

    private static Duration requirePositiveDuration(
        Duration value
    ) {

        Objects.requireNonNull(
            value,
            "leaseDuration must not be null"
        );

        if (value.isZero()
            || value.isNegative()) {

            throw new IllegalArgumentException(
                "leaseDuration must be positive"
            );
        }

        return value;
    }
}
