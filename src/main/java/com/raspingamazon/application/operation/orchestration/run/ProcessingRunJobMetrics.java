package com.raspingamazon.application.operation.orchestration.run;

/**
 * Métricas operacionais dos ProcessingJobs pertencentes à linhagem
 * de uma ProcessingRun.
 *
 * <p>Os estados correspondem diretamente aos fatos persistidos na
 * fila. Nenhuma transição de estado acontece neste read model.</p>
 *
 * <p>totalAttempts representa a soma de attempt_count dos jobs
 * relacionados à run.</p>
 *
 * <p>retryAttempts representa tentativas além da primeira tentativa
 * de cada job. A consulta JDBC será responsável por derivar esse
 * valor a partir dos fatos persistidos.</p>
 */
public record ProcessingRunJobMetrics(
    long totalJobs,
    long pendingJobs,
    long runningJobs,
    long retryWaitJobs,
    long succeededJobs,
    long deadJobs,
    long totalAttempts,
    long retryAttempts
) {

    public ProcessingRunJobMetrics {

        requireNonNegative(
            totalJobs,
            "totalJobs"
        );

        requireNonNegative(
            pendingJobs,
            "pendingJobs"
        );

        requireNonNegative(
            runningJobs,
            "runningJobs"
        );

        requireNonNegative(
            retryWaitJobs,
            "retryWaitJobs"
        );

        requireNonNegative(
            succeededJobs,
            "succeededJobs"
        );

        requireNonNegative(
            deadJobs,
            "deadJobs"
        );

        requireNonNegative(
            totalAttempts,
            "totalAttempts"
        );

        requireNonNegative(
            retryAttempts,
            "retryAttempts"
        );

        long statusTotal =
            pendingJobs
                + runningJobs
                + retryWaitJobs
                + succeededJobs
                + deadJobs;

        if (statusTotal != totalJobs) {

            throw new IllegalArgumentException(
                "ProcessingRunJobMetrics job status breakdown "
                    + "must equal totalJobs"
            );
        }

        if (retryAttempts > totalAttempts) {

            throw new IllegalArgumentException(
                "ProcessingRunJobMetrics retryAttempts "
                    + "must not exceed totalAttempts"
            );
        }
    }

    public boolean hasDeadJobs() {

        return deadJobs > 0L;
    }

    public boolean hasActiveJobs() {

        return pendingJobs > 0L
            || runningJobs > 0L
            || retryWaitJobs > 0L;
    }

    private static void requireNonNegative(
        long value,
        String fieldName
    ) {

        if (value < 0L) {

            throw new IllegalArgumentException(
                "ProcessingRunJobMetrics "
                    + fieldName
                    + " must not be negative"
            );
        }
    }
}
