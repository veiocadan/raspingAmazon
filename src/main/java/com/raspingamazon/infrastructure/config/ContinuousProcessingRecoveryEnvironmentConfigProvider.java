package com.raspingamazon.infrastructure.config;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * Carrega parâmetros técnicos do recovery de startup.
 *
 * <p>Todos possuem defaults técnicos conservadores e podem ser
 * ajustados operacionalmente sem recompilar.</p>
 */
public final class
ContinuousProcessingRecoveryEnvironmentConfigProvider {

    private static final String
        DEFAULT_PROCESSING_JOB_LEASE_DURATION =
            "PT15M";

    private static final int
        DEFAULT_PROCESSING_JOB_RECOVERY_BATCH_SIZE =
            100;

    private static final String
        DEFAULT_PUBLICATION_OUTBOX_LEASE_DURATION =
            "PT5M";

    private static final int
        DEFAULT_PUBLICATION_DISPATCH_MAX_ATTEMPTS =
            3;

    private static final int
        DEFAULT_PUBLICATION_DISPATCH_RECONCILIATION_LIMIT =
            100;

    private ContinuousProcessingRecoveryEnvironmentConfigProvider() {
    }

    public static ContinuousProcessingRecoveryConfig load() {

        return load(
            System.getenv()
        );
    }

    static ContinuousProcessingRecoveryConfig load(
        Map<String, String> environment
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        Duration processingJobLeaseDuration =
            parsePositiveDuration(
                "PROCESSING_JOB_RECOVERY_LEASE_DURATION",
                readOrDefault(
                    environment,
                    "PROCESSING_JOB_RECOVERY_LEASE_DURATION",
                    DEFAULT_PROCESSING_JOB_LEASE_DURATION
                )
            );

        int processingJobRecoveryBatchSize =
            parsePositiveInt(
                "PROCESSING_JOB_RECOVERY_BATCH_SIZE",
                environment.get(
                    "PROCESSING_JOB_RECOVERY_BATCH_SIZE"
                ),
                DEFAULT_PROCESSING_JOB_RECOVERY_BATCH_SIZE
            );

        Duration publicationOutboxLeaseDuration =
            parsePositiveDuration(
                "PUBLICATION_OUTBOX_RECOVERY_LEASE_DURATION",
                readOrDefault(
                    environment,
                    "PUBLICATION_OUTBOX_RECOVERY_LEASE_DURATION",
                    DEFAULT_PUBLICATION_OUTBOX_LEASE_DURATION
                )
            );

        int publicationDispatchMaxAttempts =
            parsePositiveInt(
                "PUBLICATION_DISPATCH_MAX_ATTEMPTS",
                environment.get(
                    "PUBLICATION_DISPATCH_MAX_ATTEMPTS"
                ),
                DEFAULT_PUBLICATION_DISPATCH_MAX_ATTEMPTS
            );

        int publicationDispatchReconciliationLimit =
            parsePositiveInt(
                "PUBLICATION_DISPATCH_RECONCILIATION_LIMIT",
                environment.get(
                    "PUBLICATION_DISPATCH_RECONCILIATION_LIMIT"
                ),
                DEFAULT_PUBLICATION_DISPATCH_RECONCILIATION_LIMIT
            );

        return new ContinuousProcessingRecoveryConfig(
            processingJobLeaseDuration,
            processingJobRecoveryBatchSize,
            publicationOutboxLeaseDuration,
            publicationDispatchMaxAttempts,
            publicationDispatchReconciliationLimit
        );
    }

    private static String readOrDefault(
        Map<String, String> environment,
        String variableName,
        String defaultValue
    ) {

        String value =
            environment.get(
                variableName
            );

        if (value == null
            || value.isBlank()) {

            return defaultValue;
        }

        return value.trim();
    }

    private static Duration parsePositiveDuration(
        String variableName,
        String value
    ) {

        final Duration duration;

        try {

            duration =
                Duration.parse(
                    value
                );

        } catch (DateTimeParseException exception) {

            throw new IllegalStateException(
                variableName
                    + " must be a positive ISO-8601 duration",
                exception
            );
        }

        if (duration.isZero()
            || duration.isNegative()) {

            throw new IllegalStateException(
                variableName
                    + " must be a positive ISO-8601 duration"
            );
        }

        return duration;
    }

    private static int parsePositiveInt(
        String variableName,
        String value,
        int defaultValue
    ) {

        if (value == null
            || value.isBlank()) {

            return defaultValue;
        }

        final int parsed;

        try {

            parsed =
                Integer.parseInt(
                    value.trim()
                );

        } catch (NumberFormatException exception) {

            throw new IllegalStateException(
                variableName
                    + " must be a positive integer",
                exception
            );
        }

        if (parsed <= 0) {

            throw new IllegalStateException(
                variableName
                    + " must be a positive integer"
            );
        }

        return parsed;
    }
}
