package com.raspingamazon.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Configuração de bootstrap do processamento contínuo.
 *
 * <p>Esta configuração contém somente parâmetros necessários para
 * colocar o runtime em operação.</p>
 *
 * <p>A frequência funcional é utilizada somente quando o schedule
 * ainda não existe. Depois da criação, o PostgreSQL permanece como
 * fonte operacional de verdade.</p>
 */
public record ContinuousProcessingEnvironmentConfig(
    String scheduleKey,
    URI source,
    Duration initialScheduleInterval,
    String schedulerInstanceId,
    String workerId,
    Duration schedulerPollInterval,
    Duration schedulerLeaseDuration,
    Duration workerIdleDelay,
    int collectionMaxAttempts,
    int enrichmentMaxAttempts,
    int evaluationMaxAttempts,
    Duration retryBaseDelay,
    Duration retryMaxDelay
) {

    public ContinuousProcessingEnvironmentConfig {

        scheduleKey =
            requireText(
                scheduleKey,
                "scheduleKey"
            );

        source =
            Objects.requireNonNull(
                source,
                "source must not be null"
            );

        if (!source.isAbsolute()) {
            throw new IllegalArgumentException(
                "source must be absolute"
            );
        }

        initialScheduleInterval =
            requirePositiveDuration(
                initialScheduleInterval,
                "initialScheduleInterval"
            );

        schedulerInstanceId =
            requireText(
                schedulerInstanceId,
                "schedulerInstanceId"
            );

        workerId =
            requireText(
                workerId,
                "workerId"
            );

        schedulerPollInterval =
            requirePositiveDuration(
                schedulerPollInterval,
                "schedulerPollInterval"
            );

        schedulerLeaseDuration =
            requirePositiveDuration(
                schedulerLeaseDuration,
                "schedulerLeaseDuration"
            );

        workerIdleDelay =
            requirePositiveDuration(
                workerIdleDelay,
                "workerIdleDelay"
            );

        collectionMaxAttempts =
            requirePositiveInt(
                collectionMaxAttempts,
                "collectionMaxAttempts"
            );

        enrichmentMaxAttempts =
            requirePositiveInt(
                enrichmentMaxAttempts,
                "enrichmentMaxAttempts"
            );

        evaluationMaxAttempts =
            requirePositiveInt(
                evaluationMaxAttempts,
                "evaluationMaxAttempts"
            );

        retryBaseDelay =
            requirePositiveDuration(
                retryBaseDelay,
                "retryBaseDelay"
            );

        retryMaxDelay =
            requirePositiveDuration(
                retryMaxDelay,
                "retryMaxDelay"
            );

        if (retryBaseDelay.compareTo(
            retryMaxDelay
        ) > 0) {

            throw new IllegalArgumentException(
                "retryBaseDelay must not be greater than retryMaxDelay"
            );
        }
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

    private static Duration requirePositiveDuration(
        Duration value,
        String name
    ) {

        Objects.requireNonNull(
            value,
            name + " must not be null"
        );

        if (value.isZero()
            || value.isNegative()) {

            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }

        return value;
    }

    private static int requirePositiveInt(
        int value,
        String name
    ) {

        if (value <= 0) {
            throw new IllegalArgumentException(
                name + " must be positive"
            );
        }

        return value;
    }
}
