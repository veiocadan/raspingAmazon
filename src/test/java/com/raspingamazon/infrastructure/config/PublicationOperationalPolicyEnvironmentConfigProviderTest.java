package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationOperationalPolicyEnvironmentConfigProviderTest {

    @Test
    void shouldLoadCompleteOperationalPublicationConfiguration() {

        PublicationOperationalPolicyConfig config =
            PublicationOperationalPolicyEnvironmentConfigProvider
                .load(
                    validEnvironment()
                );

        assertEquals(
            "TELEGRAM",
            config.channel()
        );

        assertEquals(
            "@public_channel",
            config.destination()
        );

        assertEquals(
            "QUOTA_V4",
            config.quotaProfile()
                .version()
        );

        assertEquals(
            7,
            config.quotaProfile()
                .maxPublicationsPerDay()
        );

        assertEquals(
            ZoneId.of(
                "America/Sao_Paulo"
            ),
            config.quotaProfile()
                .quotaZone()
        );

        assertEquals(
            "CADENCE_V6",
            config.cadenceProfile()
                .version()
        );

        assertEquals(
            Duration.ofMinutes(
                90
            ),
            config.cadenceProfile()
                .interval()
        );

        assertEquals(
            LocalTime.of(
                8,
                0
            ),
            config.cadenceProfile()
                .windowStart()
        );

        assertEquals(
            LocalTime.of(
                22,
                0
            ),
            config.cadenceProfile()
                .windowEnd()
        );
    }

    @Test
    void shouldRejectMissingPrimaryChannel() {

        Map<String, String> environment =
            new java.util.HashMap<>(
                validEnvironment()
            );

        environment.remove(
            "PUBLICATION_PRIMARY_CHANNEL"
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationOperationalPolicyEnvironmentConfigProvider
                    .load(
                        environment
                    )
        );
    }

    @Test
    void shouldRejectMissingPrimaryDestination() {

        Map<String, String> environment =
            new java.util.HashMap<>(
                validEnvironment()
            );

        environment.remove(
            "PUBLICATION_PRIMARY_DESTINATION"
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationOperationalPolicyEnvironmentConfigProvider
                    .load(
                        environment
                    )
        );
    }

    @Test
    void shouldRejectDifferentQuotaAndCadenceZones() {

        Map<String, String> environment =
            new java.util.HashMap<>(
                validEnvironment()
            );

        environment.put(
            "PUBLICATION_CADENCE_ZONE",
            "UTC"
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                PublicationOperationalPolicyEnvironmentConfigProvider
                    .load(
                        environment
                    )
        );
    }

    private Map<String, String> validEnvironment() {

        return Map.ofEntries(
            Map.entry(
                "PUBLICATION_PRIMARY_CHANNEL",
                "TELEGRAM"
            ),
            Map.entry(
                "PUBLICATION_PRIMARY_DESTINATION",
                "@public_channel"
            ),
            Map.entry(
                "PUBLICATION_QUOTA_VERSION",
                "QUOTA_V4"
            ),
            Map.entry(
                "PUBLICATION_QUOTA_MAX_PER_DAY",
                "7"
            ),
            Map.entry(
                "PUBLICATION_QUOTA_ZONE",
                "America/Sao_Paulo"
            ),
            Map.entry(
                "PUBLICATION_CADENCE_VERSION",
                "CADENCE_V6"
            ),
            Map.entry(
                "PUBLICATION_CADENCE_INTERVAL",
                "PT1H30M"
            ),
            Map.entry(
                "PUBLICATION_CADENCE_WINDOW_START",
                "08:00"
            ),
            Map.entry(
                "PUBLICATION_CADENCE_WINDOW_END",
                "22:00"
            ),
            Map.entry(
                "PUBLICATION_CADENCE_ZONE",
                "America/Sao_Paulo"
            )
        );
    }
}
