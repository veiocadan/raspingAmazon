package com.raspingamazon.infrastructure.config;

import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationSelectionEnvironmentConfigProviderTest {

    @Test
    void shouldLoadSelectionProfileFromEnvironment() {

        PublicationSelectionProfile profile =
            PublicationSelectionEnvironmentConfigProvider.load(
                environment(
                    "PUBLICATION_SELECTION_V1_CONFIG_V4",
                    "PT48H",
                    "PT168H"
                )
            );

        assertEquals(
            "PUBLICATION_SELECTION_V1_CONFIG_V4",
            profile.version()
        );

        assertEquals(
            Duration.ofDays(
                2
            ),
            profile.hardCooldown()
        );

        assertEquals(
            Duration.ofDays(
                7
            ),
            profile.preferredCooldown()
        );
    }

    @Test
    void shouldAllowZeroCooldowns() {

        PublicationSelectionProfile profile =
            PublicationSelectionEnvironmentConfigProvider.load(
                environment(
                    "PUBLICATION_SELECTION_V1_CONFIG_V1",
                    "PT0S",
                    "PT0S"
                )
            );

        assertEquals(
            Duration.ZERO,
            profile.hardCooldown()
        );

        assertEquals(
            Duration.ZERO,
            profile.preferredCooldown()
        );
    }

    @Test
    void shouldRejectMissingVersion() {

        Map<String, String> environment =
            new HashMap<>(
                environment(
                    "PUBLICATION_SELECTION_V1_CONFIG_V1",
                    "PT24H",
                    "PT72H"
                )
            );

        environment.remove(
            "PUBLICATION_SELECTION_VERSION"
        );

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationSelectionEnvironmentConfigProvider.load(
                    environment
                )
        );
    }

    @Test
    void shouldRejectVersionFromDifferentPolicy() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationSelectionEnvironmentConfigProvider.load(
                    environment(
                        "PUBLICATION_SELECTION_V2_CONFIG_V1",
                        "PT24H",
                        "PT72H"
                    )
                )
        );
    }

    @Test
    void shouldRejectUnqualifiedConfigurationVersion() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationSelectionEnvironmentConfigProvider.load(
                    environment(
                        "SELECTION_V1",
                        "PT24H",
                        "PT72H"
                    )
                )
        );
    }

    @Test
    void shouldRejectZeroConfigurationRevision() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationSelectionEnvironmentConfigProvider.load(
                    environment(
                        "PUBLICATION_SELECTION_V1_CONFIG_V0",
                        "PT24H",
                        "PT72H"
                    )
                )
        );
    }

    @Test
    void shouldRejectInvalidDuration() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationSelectionEnvironmentConfigProvider.load(
                    environment(
                        "PUBLICATION_SELECTION_V1_CONFIG_V1",
                        "2 DAYS",
                        "PT168H"
                    )
                )
        );
    }

    @Test
    void shouldRejectNegativeCooldown() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationSelectionEnvironmentConfigProvider.load(
                    environment(
                        "PUBLICATION_SELECTION_V1_CONFIG_V1",
                        "-PT1H",
                        "PT168H"
                    )
                )
        );
    }

    @Test
    void shouldRejectPreferredCooldownBelowHardCooldown() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationSelectionEnvironmentConfigProvider.load(
                    environment(
                        "PUBLICATION_SELECTION_V1_CONFIG_V1",
                        "PT168H",
                        "PT48H"
                    )
                )
        );
    }

    @Test
    void shouldRejectSubSecondDuration() {

        assertThrows(
            IllegalStateException.class,
            () ->
                PublicationSelectionEnvironmentConfigProvider.load(
                    environment(
                        "PUBLICATION_SELECTION_V1_CONFIG_V1",
                        "PT0.5S",
                        "PT1S"
                    )
                )
        );
    }

    private Map<String, String> environment(
        String version,
        String hardCooldown,
        String preferredCooldown
    ) {

        return Map.of(
            "PUBLICATION_SELECTION_VERSION",
            version,
            "PUBLICATION_SELECTION_HARD_COOLDOWN",
            hardCooldown,
            "PUBLICATION_SELECTION_PREFERRED_COOLDOWN",
            preferredCooldown
        );
    }
}
