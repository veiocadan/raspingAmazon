package com.raspingamazon.infrastructure.config;

import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationEnvironmentConfigProviderTest {

    @Test
    void shouldLoadPublicationQuotaFromEnvironment() {

        PublicationQuotaProfile profile =
            PublicationQuotaEnvironmentConfigProvider.load(
                Map.of(
                    "PUBLICATION_QUOTA_VERSION",
                    "QUOTA_V4",
                    "PUBLICATION_QUOTA_MAX_PER_DAY",
                    "7",
                    "PUBLICATION_QUOTA_ZONE",
                    "America/Sao_Paulo"
                )
            );

        assertEquals(
            "QUOTA_V4",
            profile.version()
        );

        assertEquals(
            7,
            profile.maxPublicationsPerDay()
        );

        assertEquals(
            ZoneId.of(
                "America/Sao_Paulo"
            ),
            profile.quotaZone()
        );
    }

    @Test
    void shouldRejectMissingQuotaConfiguration() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationQuotaEnvironmentConfigProvider.load(
                    Map.of()
                )
        );
    }

    @Test
    void shouldRejectInvalidQuotaMaximum() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationQuotaEnvironmentConfigProvider.load(
                    Map.of(
                        "PUBLICATION_QUOTA_VERSION",
                        "QUOTA_V1",
                        "PUBLICATION_QUOTA_MAX_PER_DAY",
                        "0",
                        "PUBLICATION_QUOTA_ZONE",
                        "America/Sao_Paulo"
                    )
                )
        );
    }

    @Test
    void shouldRejectInvalidQuotaZone() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationQuotaEnvironmentConfigProvider.load(
                    Map.of(
                        "PUBLICATION_QUOTA_VERSION",
                        "QUOTA_V1",
                        "PUBLICATION_QUOTA_MAX_PER_DAY",
                        "7",
                        "PUBLICATION_QUOTA_ZONE",
                        "INVALID/ZONE"
                    )
                )
        );
    }

    @Test
    void shouldLoadPublicationCadenceFromEnvironment() {

        PublicationCadenceProfile profile =
            PublicationCadenceEnvironmentConfigProvider.load(
                cadenceEnvironment(
                    "CADENCE_V3",
                    "PT1H30M",
                    "08:00",
                    "22:00"
                )
            );

        assertEquals(
            "CADENCE_V3",
            profile.version()
        );

        assertEquals(
            Duration.ofMinutes(
                90
            ),
            profile.interval()
        );

        assertEquals(
            LocalTime.of(
                8,
                0
            ),
            profile.windowStart()
        );

        assertEquals(
            LocalTime.of(
                22,
                0
            ),
            profile.windowEnd()
        );

        assertEquals(
            ZoneId.of(
                "America/Sao_Paulo"
            ),
            profile.zone()
        );
    }

    @Test
    void shouldRejectCadenceWindowThatCrossesMidnight() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                PublicationCadenceEnvironmentConfigProvider.load(
                    cadenceEnvironment(
                        "CADENCE_NIGHT_V1",
                        "PT1H",
                        "22:00",
                        "02:00"
                    )
                )
        );
    }

    @Test
    void shouldRejectInvalidCadenceDuration() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationCadenceEnvironmentConfigProvider.load(
                    cadenceEnvironment(
                        "CADENCE_V1",
                        "2 HOURS",
                        "08:00",
                        "22:00"
                    )
                )
        );
    }

    @Test
    void shouldRejectEqualCadenceWindowBoundaries() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                PublicationCadenceEnvironmentConfigProvider.load(
                    cadenceEnvironment(
                        "CADENCE_V1",
                        "PT2H",
                        "08:00",
                        "08:00"
                    )
                )
        );
    }

    @Test
    void shouldRejectMissingCadenceVariable() {

        Map<String, String> environment =
            new HashMap<>(
                cadenceEnvironment(
                    "CADENCE_V1",
                    "PT2H",
                    "08:00",
                    "22:00"
                )
            );

        environment.remove(
            "PUBLICATION_CADENCE_INTERVAL"
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationCadenceEnvironmentConfigProvider.load(
                    environment
                )
        );
    }

    private Map<String, String> cadenceEnvironment(
        String version,
        String interval,
        String windowStart,
        String windowEnd
    ) {

        return Map.of(
            "PUBLICATION_CADENCE_VERSION",
            version,
            "PUBLICATION_CADENCE_INTERVAL",
            interval,
            "PUBLICATION_CADENCE_WINDOW_START",
            windowStart,
            "PUBLICATION_CADENCE_WINDOW_END",
            windowEnd,
            "PUBLICATION_CADENCE_ZONE",
            "America/Sao_Paulo"
        );
    }
}
