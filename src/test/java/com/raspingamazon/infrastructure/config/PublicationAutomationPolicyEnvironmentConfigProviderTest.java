package com.raspingamazon.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicationAutomationPolicyEnvironmentConfigProviderTest {

    @Test
    void shouldLoadCompleteAutomaticPublicationConfiguration() {

        PublicationAutomationPolicyConfig config =
            PublicationAutomationPolicyEnvironmentConfigProvider.load(
                environment()
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
            "PUBLICATION_SELECTION_V1_CONFIG_V4",
            config.selectionProfile()
                .version()
        );

        assertEquals(
            Duration.ofDays(
                2
            ),
            config.selectionProfile()
                .hardCooldown()
        );

        assertEquals(
            Duration.ofDays(
                7
            ),
            config.selectionProfile()
                .preferredCooldown()
        );

        assertEquals(
            "QUOTA_V8",
            config.operationalPolicy()
                .quotaProfile()
                .version()
        );

        assertEquals(
            7,
            config.operationalPolicy()
                .quotaProfile()
                .maxPublicationsPerDay()
        );

        assertEquals(
            "CADENCE_V6",
            config.operationalPolicy()
                .cadenceProfile()
                .version()
        );

        assertEquals(
            Duration.ofMinutes(
                90
            ),
            config.operationalPolicy()
                .cadenceProfile()
                .interval()
        );

        assertEquals(
            LocalTime.of(
                8,
                0
            ),
            config.operationalPolicy()
                .cadenceProfile()
                .windowStart()
        );

        assertEquals(
            LocalTime.of(
                22,
                0
            ),
            config.operationalPolicy()
                .cadenceProfile()
                .windowEnd()
        );

        assertEquals(
            ZoneId.of(
                "America/Sao_Paulo"
            ),
            config.operationalPolicy()
                .quotaProfile()
                .quotaZone()
        );

        assertEquals(
            ZoneId.of(
                "America/Sao_Paulo"
            ),
            config.operationalPolicy()
                .cadenceProfile()
                .zone()
        );
    }

    private Map<String, String> environment() {

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
                "PUBLICATION_SELECTION_VERSION",
                "PUBLICATION_SELECTION_V1_CONFIG_V4"
            ),
            Map.entry(
                "PUBLICATION_SELECTION_HARD_COOLDOWN",
                "PT48H"
            ),
            Map.entry(
                "PUBLICATION_SELECTION_PREFERRED_COOLDOWN",
                "PT168H"
            ),
            Map.entry(
                "PUBLICATION_QUOTA_VERSION",
                "QUOTA_V8"
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
