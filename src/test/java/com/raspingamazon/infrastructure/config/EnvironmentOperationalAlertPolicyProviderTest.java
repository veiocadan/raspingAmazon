package com.raspingamazon.infrastructure.config;

import com.raspingamazon.application.operation.observability.alert.OperationalAlertPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentOperationalAlertPolicyProviderTest {

    @Test
    void shouldLoadExplicitPolicyFromEnvironment() {

        EnvironmentOperationalAlertPolicyProvider provider =
            new EnvironmentOperationalAlertPolicyProvider(
                validEnvironment()
            );

        OperationalAlertPolicy policy =
            provider.load();

        assertEquals(
            3,
            policy.repeatedExternalFailureThreshold()
        );

        assertEquals(
            Duration.ofSeconds(
                900
            ),
            policy.repeatedExternalFailureWindow()
        );

        assertEquals(
            5,
            policy.suspiciousCollectionLookbackRuns()
        );

        assertEquals(
            new BigDecimal(
                "0.50"
            ),
            policy.suspiciousCollectionDropFraction()
        );

        assertEquals(
            20L,
            policy.suspiciousCollectionMinimumBaselineCandidates()
        );
    }

    @Test
    void shouldRejectMissingRequiredVariable() {

        Map<String, String> environment =
            validEnvironment();

        environment.remove(
            EnvironmentOperationalAlertPolicyProvider
                .REPEATED_EXTERNAL_FAILURE_THRESHOLD
        );

        EnvironmentOperationalAlertPolicyProvider provider =
            new EnvironmentOperationalAlertPolicyProvider(
                environment
            );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                provider::load
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    EnvironmentOperationalAlertPolicyProvider
                        .REPEATED_EXTERNAL_FAILURE_THRESHOLD
                )
        );
    }

    @Test
    void shouldRejectInvalidIntegerValue() {

        Map<String, String> environment =
            validEnvironment();

        environment.put(
            EnvironmentOperationalAlertPolicyProvider
                .SUSPICIOUS_COLLECTION_LOOKBACK_RUNS,
            "not-an-integer"
        );

        EnvironmentOperationalAlertPolicyProvider provider =
            new EnvironmentOperationalAlertPolicyProvider(
                environment
            );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                provider::load
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    EnvironmentOperationalAlertPolicyProvider
                        .SUSPICIOUS_COLLECTION_LOOKBACK_RUNS
                )
        );
    }

    @Test
    void shouldRejectInvalidDecimalValue() {

        Map<String, String> environment =
            validEnvironment();

        environment.put(
            EnvironmentOperationalAlertPolicyProvider
                .SUSPICIOUS_COLLECTION_DROP_FRACTION,
            "fifty-percent"
        );

        EnvironmentOperationalAlertPolicyProvider provider =
            new EnvironmentOperationalAlertPolicyProvider(
                environment
            );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                provider::load
            );

        assertTrue(
            exception.getMessage()
                .contains(
                    EnvironmentOperationalAlertPolicyProvider
                        .SUSPICIOUS_COLLECTION_DROP_FRACTION
                )
        );
    }

    @Test
    void shouldRejectSemanticallyInvalidPolicy() {

        Map<String, String> environment =
            validEnvironment();

        /*
         * O contrato exige threshold >= 2.
         */
        environment.put(
            EnvironmentOperationalAlertPolicyProvider
                .REPEATED_EXTERNAL_FAILURE_THRESHOLD,
            "1"
        );

        EnvironmentOperationalAlertPolicyProvider provider =
            new EnvironmentOperationalAlertPolicyProvider(
                environment
            );

        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                provider::load
            );

        assertEquals(
            "Invalid operational alert policy configuration",
            exception.getMessage()
        );

        assertTrue(
            exception.getCause()
                instanceof IllegalArgumentException
        );
    }

    private Map<String, String> validEnvironment() {

        Map<String, String> environment =
            new HashMap<>();

        environment.put(
            EnvironmentOperationalAlertPolicyProvider
                .REPEATED_EXTERNAL_FAILURE_THRESHOLD,
            "3"
        );

        environment.put(
            EnvironmentOperationalAlertPolicyProvider
                .REPEATED_EXTERNAL_FAILURE_WINDOW_SECONDS,
            "900"
        );

        environment.put(
            EnvironmentOperationalAlertPolicyProvider
                .SUSPICIOUS_COLLECTION_LOOKBACK_RUNS,
            "5"
        );

        environment.put(
            EnvironmentOperationalAlertPolicyProvider
                .SUSPICIOUS_COLLECTION_DROP_FRACTION,
            "0.50"
        );

        environment.put(
            EnvironmentOperationalAlertPolicyProvider
                .SUSPICIOUS_COLLECTION_MINIMUM_BASELINE_CANDIDATES,
            "20"
        );

        return environment;
    }
}
