package com.raspingamazon.application.operation.orchestration.run;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Métricas operacionais agregadas de uma integração dentro de uma
 * ProcessingRun.
 *
 * <p>Os valores são derivados exclusivamente das IntegrationObservations
 * persistidas para a execução. Este read model não representa uma segunda
 * fonte de contadores.</p>
 *
 * <p>Falhas externas e internas permanecem dimensões independentes da
 * classificação TRANSIENT/PERMANENT usada pelo processamento.</p>
 */
public record ProcessingRunIntegrationMetrics(
    String integration,
    long observations,
    long successes,
    long failures,
    long externalFailures,
    long internalFailures,
    BigDecimal averageDurationMs,
    long maximumDurationMs
) {

    public ProcessingRunIntegrationMetrics {

        Objects.requireNonNull(
            integration,
            "ProcessingRunIntegrationMetrics integration must not be null"
        );

        if (integration.isBlank()) {

            throw new IllegalArgumentException(
                "ProcessingRunIntegrationMetrics integration "
                    + "must not be blank"
            );
        }

        requirePositive(
            observations,
            "observations"
        );

        requireNonNegative(
            successes,
            "successes"
        );

        requireNonNegative(
            failures,
            "failures"
        );

        requireNonNegative(
            externalFailures,
            "externalFailures"
        );

        requireNonNegative(
            internalFailures,
            "internalFailures"
        );

        if (successes + failures != observations) {

            throw new IllegalArgumentException(
                "ProcessingRunIntegrationMetrics success/failure "
                    + "breakdown must equal observations"
            );
        }

        if (externalFailures + internalFailures != failures) {

            throw new IllegalArgumentException(
                "ProcessingRunIntegrationMetrics failure origin "
                    + "breakdown must equal failures"
            );
        }

        Objects.requireNonNull(
            averageDurationMs,
            "ProcessingRunIntegrationMetrics averageDurationMs "
                + "must not be null"
        );

        if (averageDurationMs.signum() < 0) {

            throw new IllegalArgumentException(
                "ProcessingRunIntegrationMetrics averageDurationMs "
                    + "must not be negative"
            );
        }

        requireNonNegative(
            maximumDurationMs,
            "maximumDurationMs"
        );

        if (averageDurationMs.compareTo(
            BigDecimal.valueOf(
                maximumDurationMs
            )
        ) > 0) {

            throw new IllegalArgumentException(
                "ProcessingRunIntegrationMetrics averageDurationMs "
                    + "must not exceed maximumDurationMs"
            );
        }
    }

    public boolean hasFailures() {
        return failures > 0L;
    }

    private static void requirePositive(
        long value,
        String fieldName
    ) {

        if (value <= 0L) {

            throw new IllegalArgumentException(
                "ProcessingRunIntegrationMetrics "
                    + fieldName
                    + " must be positive"
            );
        }
    }

    private static void requireNonNegative(
        long value,
        String fieldName
    ) {

        if (value < 0L) {

            throw new IllegalArgumentException(
                "ProcessingRunIntegrationMetrics "
                    + fieldName
                    + " must not be negative"
            );
        }
    }
}
