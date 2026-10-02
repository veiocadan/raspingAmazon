package com.raspingamazon.domain.publication.selection;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationSelectionPolicyProfileVersionTest {

    private static final Instant NOW =
        Instant.parse(
            "2026-09-30T22:00:00Z"
        );

    private final PublicationSelectionPolicy policy =
        new PublicationSelectionPolicy();

    @Test
    void shouldAcceptLegacyExactPolicyVersion() {

        assertTrue(
            PublicationSelectionPolicy.supportsProfileVersion(
                "PUBLICATION_SELECTION_V1"
            )
        );

        assertDoesNotThrow(
            () ->
                policy.prioritize(
                    List.of(),
                    profile(
                        "PUBLICATION_SELECTION_V1"
                    ),
                    NOW
                )
        );
    }

    @Test
    void shouldAcceptConfigurationRevisionsForCurrentPolicy() {

        assertTrue(
            PublicationSelectionPolicy.supportsProfileVersion(
                "PUBLICATION_SELECTION_V1_CONFIG_V1"
            )
        );

        assertTrue(
            PublicationSelectionPolicy.supportsProfileVersion(
                "PUBLICATION_SELECTION_V1_CONFIG_V27"
            )
        );

        assertTrue(
            PublicationSelectionPolicy.supportsProfileVersion(
                "PUBLICATION_SELECTION_V1_CONFIG_V999999"
            )
        );

        assertDoesNotThrow(
            () ->
                policy.prioritize(
                    List.of(),
                    profile(
                        "PUBLICATION_SELECTION_V1_CONFIG_V27"
                    ),
                    NOW
                )
        );
    }

    @Test
    void shouldRejectConfigurationForAnotherPolicyVersion() {

        assertFalse(
            PublicationSelectionPolicy.supportsProfileVersion(
                "PUBLICATION_SELECTION_V2_CONFIG_V1"
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                policy.prioritize(
                    List.of(),
                    profile(
                        "PUBLICATION_SELECTION_V2_CONFIG_V1"
                    ),
                    NOW
                )
        );
    }

    @Test
    void shouldRejectUnqualifiedVersion() {

        assertFalse(
            PublicationSelectionPolicy.supportsProfileVersion(
                "SELECTION_V1"
            )
        );

        assertThrows(
            IllegalArgumentException.class,
            () ->
                policy.prioritize(
                    List.of(),
                    profile(
                        "SELECTION_V1"
                    ),
                    NOW
                )
        );
    }

    @Test
    void shouldRejectInvalidConfigurationRevision() {

        assertFalse(
            PublicationSelectionPolicy.supportsProfileVersion(
                "PUBLICATION_SELECTION_V1_CONFIG_V0"
            )
        );

        assertFalse(
            PublicationSelectionPolicy.supportsProfileVersion(
                "PUBLICATION_SELECTION_V1_CONFIG_V01"
            )
        );

        assertFalse(
            PublicationSelectionPolicy.supportsProfileVersion(
                "PUBLICATION_SELECTION_V1_CONFIG_VABC"
            )
        );

        assertFalse(
            PublicationSelectionPolicy.supportsProfileVersion(
                "PUBLICATION_SELECTION_V1_CONFIG_V"
            )
        );
    }

    @Test
    void shouldRejectNullVersion() {

        assertFalse(
            PublicationSelectionPolicy.supportsProfileVersion(
                null
            )
        );
    }

    private PublicationSelectionProfile profile(
        String version
    ) {

        return new PublicationSelectionProfile(
            version,
            Duration.ofDays(
                2
            ),
            Duration.ofDays(
                7
            )
        );
    }
}
