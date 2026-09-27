package com.raspingamazon.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Carrega a configuração do runtime contínuo a partir do ambiente.
 *
 * <p>A frequência inicial do schedule é deliberadamente obrigatória.
 * Não existe cadência funcional escondida no código.</p>
 */
public final class ContinuousProcessingEnvironmentConfigProvider {

    private static final String DEFAULT_SCHEDULE_KEY =
        "amazon-deals";

    private static final String DEFAULT_SOURCE =
        "https://www.amazon.com.br/deals";

    private static final String DEFAULT_SCHEDULER_POLL_INTERVAL =
        "PT2S";

    private static final String DEFAULT_SCHEDULER_LEASE_DURATION =
        "PT1M";

    private static final String DEFAULT_WORKER_IDLE_DELAY =
        "PT0.25S";

    private static final int DEFAULT_COLLECTION_MAX_ATTEMPTS =
        3;

    private static final int DEFAULT_ENRICHMENT_MAX_ATTEMPTS =
        3;

    private static final int DEFAULT_EVALUATION_MAX_ATTEMPTS =
        3;

    private static final String DEFAULT_RETRY_BASE_DELAY =
        "PT30S";

    private static final String DEFAULT_RETRY_MAX_DELAY =
        "PT5M";

    private ContinuousProcessingEnvironmentConfigProvider() {
    }

    public static ContinuousProcessingEnvironmentConfig load() {

        return load(
            System.getenv(),
            UUID.randomUUID()
                .toString()
        );
    }

    static ContinuousProcessingEnvironmentConfig load(
        Map<String, String> environment,
        String runtimeInstanceId
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        String validatedRuntimeInstanceId =
            requireText(
                runtimeInstanceId,
                "runtimeInstanceId"
            );

        String scheduleKey =
            readOrDefault(
                environment,
                "PROCESSING_SCHEDULE_KEY",
                DEFAULT_SCHEDULE_KEY
            );

        URI source =
            parseAbsoluteUri(
                "PROCESSING_SOURCE_URI",
                readOrDefault(
                    environment,
                    "PROCESSING_SOURCE_URI",
                    DEFAULT_SOURCE
                )
            );

        /*
         * Deliberadamente obrigatório.
         *
         * Esta é a cadência funcional da coleta e, portanto, não deve
         * existir como valor invisível compilado no código.
         */
        Duration initialScheduleInterval =
            parsePositiveDuration(
                "PROCESSING_SCHEDULE_INTERVAL",
                readRequired(
                    environment,
                    "PROCESSING_SCHEDULE_INTERVAL"
                )
            );

        String schedulerInstanceId =
            readOrDefault(
                environment,
                "PROCESSING_SCHEDULER_INSTANCE_ID",
                "scheduler-"
                    + validatedRuntimeInstanceId
            );

        String workerId =
            readOrDefault(
                environment,
                "PROCESSING_WORKER_ID",
                "worker-"
                    + validatedRuntimeInstanceId
            );

        Duration schedulerPollInterval =
            parsePositiveDuration(
                "PROCESSING_SCHEDULER_POLL_INTERVAL",
                readOrDefault(
                    environment,
                    "PROCESSING_SCHEDULER_POLL_INTERVAL",
                    DEFAULT_SCHEDULER_POLL_INTERVAL
                )
            );

        Duration schedulerLeaseDuration =
            parsePositiveDuration(
                "PROCESSING_SCHEDULER_LEASE_DURATION",
                readOrDefault(
                    environment,
                    "PROCESSING_SCHEDULER_LEASE_DURATION",
                    DEFAULT_SCHEDULER_LEASE_DURATION
                )
            );

        Duration workerIdleDelay =
            parsePositiveDuration(
                "PROCESSING_WORKER_IDLE_DELAY",
                readOrDefault(
                    environment,
                    "PROCESSING_WORKER_IDLE_DELAY",
                    DEFAULT_WORKER_IDLE_DELAY
                )
            );

        int collectionMaxAttempts =
            parsePositiveInt(
                "PROCESSING_COLLECTION_MAX_ATTEMPTS",
                environment.get(
                    "PROCESSING_COLLECTION_MAX_ATTEMPTS"
                ),
                DEFAULT_COLLECTION_MAX_ATTEMPTS
            );

        int enrichmentMaxAttempts =
            parsePositiveInt(
                "PROCESSING_ENRICHMENT_MAX_ATTEMPTS",
                environment.get(
                    "PROCESSING_ENRICHMENT_MAX_ATTEMPTS"
                ),
                DEFAULT_ENRICHMENT_MAX_ATTEMPTS
            );

        int evaluationMaxAttempts =
            parsePositiveInt(
                "PROCESSING_EVALUATION_MAX_ATTEMPTS",
                environment.get(
                    "PROCESSING_EVALUATION_MAX_ATTEMPTS"
                ),
                DEFAULT_EVALUATION_MAX_ATTEMPTS
            );

        Duration retryBaseDelay =
            parsePositiveDuration(
                "PROCESSING_RETRY_BASE_DELAY",
                readOrDefault(
                    environment,
                    "PROCESSING_RETRY_BASE_DELAY",
                    DEFAULT_RETRY_BASE_DELAY
                )
            );

        Duration retryMaxDelay =
            parsePositiveDuration(
                "PROCESSING_RETRY_MAX_DELAY",
                readOrDefault(
                    environment,
                    "PROCESSING_RETRY_MAX_DELAY",
                    DEFAULT_RETRY_MAX_DELAY
                )
            );

        return new ContinuousProcessingEnvironmentConfig(
            scheduleKey,
            source,
            initialScheduleInterval,
            schedulerInstanceId,
            workerId,
            schedulerPollInterval,
            schedulerLeaseDuration,
            workerIdleDelay,
            collectionMaxAttempts,
            enrichmentMaxAttempts,
            evaluationMaxAttempts,
            retryBaseDelay,
            retryMaxDelay
        );
    }

    private static String readRequired(
        Map<String, String> environment,
        String variableName
    ) {

        String value =
            environment.get(
                variableName
            );

        if (value == null
            || value.isBlank()) {

            throw new IllegalStateException(
                "Required environment variable is missing: "
                    + variableName
            );
        }

        return value.trim();
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

    private static URI parseAbsoluteUri(
        String variableName,
        String value
    ) {

        final URI uri;

        try {

            uri =
                URI.create(
                    value
                );

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                variableName
                    + " must be a valid absolute URI",
                exception
            );
        }

        if (!uri.isAbsolute()) {

            throw new IllegalStateException(
                variableName
                    + " must be a valid absolute URI"
            );
        }

        return uri;
    }

    private static String requireText(
        String value,
        String name
    ) {

        Objects.requireNonNull(
            value,
            name + " must not be null"
        );

        if (value.isBlank()) {

            throw new IllegalArgumentException(
                name + " must not be blank"
            );
        }

        return value;
    }
}
