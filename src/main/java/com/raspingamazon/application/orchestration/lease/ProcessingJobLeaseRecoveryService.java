package com.raspingamazon.application.orchestration.lease;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Caso de uso responsável por executar recuperação de ProcessingJobs
 * cujo lease expirou.
 *
 * <p>A política de expiração pertence à aplicação. O adapter de
 * persistência recebe apenas o limite temporal já calculado.</p>
 *
 * <p>{@link #recoverOnce()} preserva a operação histórica de uma única
 * página. {@link #recoverUntilQuiescent()} é a operação adequada para
 * startup recovery: ela drena páginas completas até que a autoridade
 * persistente informe uma página parcial.</p>
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
     * Executa uma única página de recuperação.
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

        return Objects.requireNonNull(
            recoveryPort.recoverExpiredLeases(
                leaseExpiredBefore,
                recoveredAt,
                batchSize
            ),
            "recoveryPort returned null"
        );
    }

    /**
     * Drena todos os leases expirados visíveis até quiescência.
     *
     * <p>Uma página com menos itens que {@code batchSize} prova que
     * não havia outra página completa no instante da consulta. Cada
     * página completa obrigatoriamente representa progresso porque a
     * implementação JDBC remove os itens recuperados do estado
     * RUNNING.</p>
     *
     * <p>Se uma implementação da porta devolver mais itens que o
     * tamanho solicitado, o startup falha fechado.</p>
     */
    public ProcessingJobLeaseRecoveryResult recoverUntilQuiescent() {

        int totalRetryWaitCount =
            0;

        int totalDeadCount =
            0;

        while (true) {

            ProcessingJobLeaseRecoveryResult page =
                recoverOnce();

            int recoveredInPage =
                page.totalRecovered();

            if (recoveredInPage > batchSize) {

                throw new IllegalStateException(
                    "ProcessingJob recovery returned more items "
                        + "than requested batchSize"
                );
            }

            totalRetryWaitCount =
                addExact(
                    totalRetryWaitCount,
                    page.retryWaitCount(),
                    "retryWaitCount"
                );

            totalDeadCount =
                addExact(
                    totalDeadCount,
                    page.deadCount(),
                    "deadCount"
                );

            if (recoveredInPage < batchSize) {

                return new ProcessingJobLeaseRecoveryResult(
                    totalRetryWaitCount,
                    totalDeadCount
                );
            }
        }
    }

    private static int addExact(
        int current,
        int increment,
        String fieldName
    ) {

        try {

            return Math.addExact(
                current,
                increment
            );

        } catch (ArithmeticException exception) {

            throw new IllegalStateException(
                "Aggregated ProcessingJob recovery "
                    + fieldName
                    + " overflowed",
                exception
            );
        }
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
