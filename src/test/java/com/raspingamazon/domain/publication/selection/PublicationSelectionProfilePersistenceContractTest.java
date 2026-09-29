package com.raspingamazon.domain.publication.selection;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationSelectionProfilePersistenceContractTest {

    @Test
    void shouldRejectFractionalSecondHardCooldown() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionProfile(
                    PublicationSelectionPolicy.VERSION,
                    Duration.ofMillis(
                        500L
                    ),
                    Duration.ofSeconds(
                        1L
                    )
                )
        );
    }

    @Test
    void shouldRejectFractionalSecondPreferredCooldown() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationSelectionProfile(
                    PublicationSelectionPolicy.VERSION,
                    Duration.ZERO,
                    Duration.ofMillis(
                        500L
                    )
                )
        );
    }
}
