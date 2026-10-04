package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContinuousProcessingRecoveryEnvironmentConfigProviderTest {

    @Test
    void shouldLoadConservativeTechnicalDefaults() {

        ContinuousProcessingRecoveryConfig config =
            ContinuousProcessingRecoveryEnvironmentConfigProvider.load(
                Map.of()
            );

        assertEquals(
            Duration.ofMinutes(
                15
            ),
            config.processingJobLeaseDuration()
        );

        assertEquals(
            100,
            config.processingJobRecoveryBatchSize()
        );

        assertEquals(
            Duration.ofMinutes(
                5
            ),
            config.publicationOutboxLeaseDuration()
        );

        assertEquals(
            3,
            config.publicationDispatchMaxAttempts()
        );

        assertEquals(
            100,
            config.publicationDispatchReconciliationLimit()
        );
    }

    @Test
    void shouldLoadOperationalOverrides() {

        ContinuousProcessingRecoveryConfig config =
            ContinuousProcessingRecoveryEnvironmentConfigProvider.load(
                Map.of(
                    "PROCESSING_JOB_RECOVERY_LEASE_DURATION",
                    "PT30M",
                    "PROCESSING_JOB_RECOVERY_BATCH_SIZE",
                    "25",
                    "PUBLICATION_OUTBOX_RECOVERY_LEASE_DURATION",
                    "PT10M",
                    "PUBLICATION_DISPATCH_MAX_ATTEMPTS",
                    "5",
                    "PUBLICATION_DISPATCH_RECONCILIATION_LIMIT",
                    "40"
                )
            );

        assertEquals(
            Duration.ofMinutes(
                30
            ),
            config.processingJobLeaseDuration()
        );

        assertEquals(
            25,
            config.processingJobRecoveryBatchSize()
        );

        assertEquals(
            Duration.ofMinutes(
                10
            ),
            config.publicationOutboxLeaseDuration()
        );

        assertEquals(
            5,
            config.publicationDispatchMaxAttempts()
        );

        assertEquals(
            40,
            config.publicationDispatchReconciliationLimit()
        );
    }

    @Test
    void shouldRejectInvalidLeaseDuration() {

        assertThrows(
            IllegalStateException.class,
            () ->
                ContinuousProcessingRecoveryEnvironmentConfigProvider.load(
                    Map.of(
                        "PROCESSING_JOB_RECOVERY_LEASE_DURATION",
                        "PT0S"
                    )
                )
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                ContinuousProcessingRecoveryEnvironmentConfigProvider.load(
                    Map.of(
                        "PUBLICATION_OUTBOX_RECOVERY_LEASE_DURATION",
                        "not-a-duration"
                    )
                )
        );
    }

    @Test
    void shouldRejectNonPositiveIntegerSettings() {

        assertThrows(
            IllegalStateException.class,
            () ->
                ContinuousProcessingRecoveryEnvironmentConfigProvider.load(
                    Map.of(
                        "PROCESSING_JOB_RECOVERY_BATCH_SIZE",
                        "0"
                    )
                )
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                ContinuousProcessingRecoveryEnvironmentConfigProvider.load(
                    Map.of(
                        "PUBLICATION_DISPATCH_MAX_ATTEMPTS",
                        "-1"
                    )
                )
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                ContinuousProcessingRecoveryEnvironmentConfigProvider.load(
                    Map.of(
                        "PUBLICATION_DISPATCH_RECONCILIATION_LIMIT",
                        "0"
                    )
                )
        );
    }
}
