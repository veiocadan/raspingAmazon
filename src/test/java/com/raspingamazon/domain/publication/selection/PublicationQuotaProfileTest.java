package com.raspingamazon.domain.publication.selection;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PublicationQuotaProfileTest {

    private static final ZoneId QUOTA_ZONE =
        ZoneId.of(
            "America/Sao_Paulo"
        );

    @Test
    void shouldCreateValidQuotaProfile() {

        PublicationQuotaProfile profile =
            new PublicationQuotaProfile(
                "PUBLICATION_QUOTA_V1",
                7,
                QUOTA_ZONE
            );

        assertEquals(
            "PUBLICATION_QUOTA_V1",
            profile.version()
        );

        assertEquals(
            7,
            profile.maxPublicationsPerDay()
        );

        assertEquals(
            QUOTA_ZONE,
            profile.quotaZone()
        );
    }

    @Test
    void shouldResolveQuotaDateUsingConfiguredZone() {

        PublicationQuotaProfile profile =
            new PublicationQuotaProfile(
                "PUBLICATION_QUOTA_V1",
                7,
                QUOTA_ZONE
            );

        Instant instant =
            Instant.parse(
                "2026-09-28T02:30:00Z"
            );

        assertEquals(
            LocalDate.of(
                2026,
                9,
                27
            ),
            profile.quotaDateAt(
                instant
            )
        );
    }

    @Test
    void shouldRejectBlankVersion() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationQuotaProfile(
                    "   ",
                    7,
                    QUOTA_ZONE
                )
        );
    }

    @Test
    void shouldRejectZeroDailyQuota() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationQuotaProfile(
                    "PUBLICATION_QUOTA_V1",
                    0,
                    QUOTA_ZONE
                )
        );
    }

    @Test
    void shouldRejectNegativeDailyQuota() {

        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PublicationQuotaProfile(
                    "PUBLICATION_QUOTA_V1",
                    -1,
                    QUOTA_ZONE
                )
        );
    }

    @Test
    void shouldRejectNullQuotaZone() {

        assertThrows(
            NullPointerException.class,
            () ->
                new PublicationQuotaProfile(
                    "PUBLICATION_QUOTA_V1",
                    7,
                    null
                )
        );
    }

    @Test
    void shouldRejectNullCurrentTimeWhenResolvingQuotaDate() {

        PublicationQuotaProfile profile =
            new PublicationQuotaProfile(
                "PUBLICATION_QUOTA_V1",
                7,
                QUOTA_ZONE
            );

        assertThrows(
            NullPointerException.class,
            () ->
                profile.quotaDateAt(
                    null
                )
        );
    }
}
