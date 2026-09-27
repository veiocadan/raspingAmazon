package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContinuousProcessingEnvironmentConfigProviderTest {

    @Test
    void shouldRequireExplicitScheduleInterval() {

        Map<String, String> environment =
            Map.of();

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () ->
                    ContinuousProcessingEnvironmentConfigProvider.load(
                        environment,
                        "runtime-test"
                    )
            );

        assertEquals(
            "Required environment variable is missing: "
                + "PROCESSING_SCHEDULE_INTERVAL",
            exception.getMessage()
        );
    }

    @Test
    void shouldLoadDefaultsAroundExplicitFunctionalCadence() {

        Map<String, String> environment =
            Map.of(
                "PROCESSING_SCHEDULE_INTERVAL",
                "PT15M"
            );

        ContinuousProcessingEnvironmentConfig config =
            ContinuousProcessingEnvironmentConfigProvider.load(
                environment,
                "runtime-123"
            );

        assertEquals(
            "amazon-deals",
            config.scheduleKey()
        );

        assertEquals(
            "https://www.amazon.com.br/deals",
            config.source()
                .toString()
        );

        assertEquals(
            Duration.ofMinutes(
                15
            ),
            config.initialScheduleInterval()
        );

        assertEquals(
            "scheduler-runtime-123",
            config.schedulerInstanceId()
        );

        assertEquals(
            "worker-runtime-123",
            config.workerId()
        );

        assertEquals(
            Duration.ofSeconds(
                2
            ),
            config.schedulerPollInterval()
        );

        assertEquals(
            Duration.ofMinutes(
                1
            ),
            config.schedulerLeaseDuration()
        );

        assertEquals(
            Duration.ofMillis(
                250
            ),
            config.workerIdleDelay()
        );

        assertEquals(
            3,
            config.collectionMaxAttempts()
        );

        assertEquals(
            Duration.ofSeconds(
                30
            ),
            config.retryBaseDelay()
        );

        assertEquals(
            Duration.ofMinutes(
                5
            ),
            config.retryMaxDelay()
        );
    }

    @Test
    void shouldLoadOperationalOverrides() {

        Map<String, String> environment =
            new HashMap<>();

        environment.put(
            "PROCESSING_SCHEDULE_INTERVAL",
            "PT30M"
        );

        environment.put(
            "PROCESSING_SCHEDULE_KEY",
            "amazon-deals-custom"
        );

        environment.put(
            "PROCESSING_SCHEDULER_INSTANCE_ID",
            "scheduler-a"
        );

        environment.put(
            "PROCESSING_WORKER_ID",
            "worker-a"
        );

        environment.put(
            "PROCESSING_SCHEDULER_POLL_INTERVAL",
            "PT5S"
        );

        environment.put(
            "PROCESSING_SCHEDULER_LEASE_DURATION",
            "PT2M"
        );

        environment.put(
            "PROCESSING_WORKER_IDLE_DELAY",
            "PT1S"
        );

        environment.put(
            "PROCESSING_COLLECTION_MAX_ATTEMPTS",
            "4"
        );

        environment.put(
            "PROCESSING_ENRICHMENT_MAX_ATTEMPTS",
            "5"
        );

        environment.put(
            "PROCESSING_EVALUATION_MAX_ATTEMPTS",
            "6"
        );

        environment.put(
            "PROCESSING_RETRY_BASE_DELAY",
            "PT10S"
        );

        environment.put(
            "PROCESSING_RETRY_MAX_DELAY",
            "PT2M"
        );

        ContinuousProcessingEnvironmentConfig config =
            ContinuousProcessingEnvironmentConfigProvider.load(
                environment,
                "ignored-runtime-id"
            );

        assertEquals(
            "amazon-deals-custom",
            config.scheduleKey()
        );

        assertEquals(
            "scheduler-a",
            config.schedulerInstanceId()
        );

        assertEquals(
            "worker-a",
            config.workerId()
        );

        assertEquals(
            Duration.ofMinutes(
                30
            ),
            config.initialScheduleInterval()
        );

        assertEquals(
            Duration.ofSeconds(
                5
            ),
            config.schedulerPollInterval()
        );

        assertEquals(
            4,
            config.collectionMaxAttempts()
        );

        assertEquals(
            5,
            config.enrichmentMaxAttempts()
        );

        assertEquals(
            6,
            config.evaluationMaxAttempts()
        );
    }

    @Test
    void shouldRejectInvalidDuration() {

        Map<String, String> environment =
            Map.of(
                "PROCESSING_SCHEDULE_INTERVAL",
                "15-minutes"
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                ContinuousProcessingEnvironmentConfigProvider.load(
                    environment,
                    "runtime-test"
                )
        );
    }

    @Test
    void shouldRejectNonPositiveAttempts() {

        Map<String, String> environment =
            Map.of(
                "PROCESSING_SCHEDULE_INTERVAL",
                "PT15M",
                "PROCESSING_COLLECTION_MAX_ATTEMPTS",
                "0"
            );

        assertThrows(
            IllegalStateException.class,
            () ->
                ContinuousProcessingEnvironmentConfigProvider.load(
                    environment,
                    "runtime-test"
                )
        );
    }
}
