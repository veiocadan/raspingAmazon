package com.raspingamazon.domain.publication.selection;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationSelectionProfileTest {

    @Test
    void shouldCreateValidProfile() {

        PublicationSelectionProfile profile =
            new PublicationSelectionProfile(
                "PUBLICATION_SELECTION_V1",
                Duration.ofDays(
                    2
                ),
                Duration.ofDays(
                    7
                )
            );

        assertEquals(
            "PUBLICATION_SELECTION_V1",
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
            new PublicationSelectionProfile(
                "PUBLICATION_SELECTION_V1",
                Duration.ZERO,
                Duration.ZERO
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
    void shouldAllowPreferredCooldownEqualToHardCooldown() {

        Duration cooldown =
            Duration.ofHours(
                12
            );

        PublicationSelectionProfile profile =
            new PublicationSelectionProfile(
                "PUBLICATION_SELECTION_V1",
                cooldown,
                cooldown
            );

        assertEquals(
            cooldown,
            profile.hardCooldown()
        );

        assertEquals(
            cooldown,
            profile.preferredCooldown()
        );
    }

    @Test
    void shouldRejectBlankVersion() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionProfile(
                    "   ",
                    Duration.ZERO,
                    Duration.ZERO
                )
        );
    }

    @Test
    void shouldRejectNullHardCooldown() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationSelectionProfile(
                    "PUBLICATION_SELECTION_V1",
                    null,
                    Duration.ZERO
                )
        );
    }

    @Test
    void shouldRejectNullPreferredCooldown() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationSelectionProfile(
                    "PUBLICATION_SELECTION_V1",
                    Duration.ZERO,
                    null
                )
        );
    }

    @Test
    void shouldRejectNegativeHardCooldown() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionProfile(
                    "PUBLICATION_SELECTION_V1",
                    Duration.ofSeconds(
                        -1L
                    ),
                    Duration.ZERO
                )
        );
    }

    @Test
    void shouldRejectNegativePreferredCooldown() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionProfile(
                    "PUBLICATION_SELECTION_V1",
                    Duration.ZERO,
                    Duration.ofSeconds(
                        -1L
                    )
                )
        );
    }

    @Test
    void shouldRejectPreferredCooldownShorterThanHardCooldown() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionProfile(
                    "PUBLICATION_SELECTION_V1",
                    Duration.ofDays(
                        7
                    ),
                    Duration.ofDays(
                        2
                    )
                )
        );
    }
}
