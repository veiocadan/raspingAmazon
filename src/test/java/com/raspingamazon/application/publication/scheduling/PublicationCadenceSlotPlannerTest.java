package com.raspingamazon.application.publication.scheduling;

import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationCadenceSlotPlannerTest {

    private static final LocalDate QUOTA_DATE =
        LocalDate.of(
            2026,
            9,
            30
        );

    private static final ZoneId ZONE =
        ZoneId.of(
            "America/Sao_Paulo"
        );

    private final PublicationCadenceSlotPlanner planner =
        new PublicationCadenceSlotPlanner();

    @Test
    void shouldStartAtWindowOpeningWhenCurrentTimeIsBeforeWindow() {

        PublicationCadencePlan plan =
            planner.plan(
                quota(
                    7,
                    0L
                ),
                cadence(
                    Duration.ofHours(
                        2
                    )
                ),
                Instant.parse(
                    "2026-09-30T10:30:00Z"
                ),
                3,
                Optional.empty()
            );

        assertEquals(
            List.of(
                atLocalTime(
                    8,
                    0
                ),
                atLocalTime(
                    10,
                    0
                ),
                atLocalTime(
                    12,
                    0
                )
            ),
            plan.availableAtSlots()
        );

        assertEquals(
            3,
            plan.plannedSlots()
        );

        assertEquals(
            0,
            plan.quotaRejectedSlots()
        );

        assertEquals(
            0,
            plan.cadenceUnavailableSlots()
        );

        assertTrue(
            plan.fullyPlanned()
        );
    }

    @Test
    void shouldAlignToNextCadenceSlotWhenCurrentTimeIsInsideWindow() {

        PublicationCadencePlan plan =
            planner.plan(
                quota(
                    7,
                    0L
                ),
                cadence(
                    Duration.ofHours(
                        2
                    )
                ),
                Instant.parse(
                    "2026-09-30T12:10:00Z"
                ),
                2,
                Optional.empty()
            );

        assertEquals(
            List.of(
                atLocalTime(
                    10,
                    0
                ),
                atLocalTime(
                    12,
                    0
                )
            ),
            plan.availableAtSlots()
        );
    }

    @Test
    void shouldContinueAfterLastPersistedReservation() {

        PublicationCadencePlan plan =
            planner.plan(
                quota(
                    7,
                    1L
                ),
                cadence(
                    Duration.ofHours(
                        2
                    )
                ),
                Instant.parse(
                    "2026-09-30T14:00:00Z"
                ),
                2,
                Optional.of(
                    atLocalTime(
                        14,
                        0
                    )
                )
            );

        assertEquals(
            List.of(
                atLocalTime(
                    16,
                    0
                ),
                atLocalTime(
                    18,
                    0
                )
            ),
            plan.availableAtSlots()
        );
    }

    @Test
    void shouldAcceptReservationCreatedAfterQuotaSnapshot() {

        PublicationCadencePlan plan =
            planner.plan(
                quota(
                    7,
                    0L
                ),
                cadence(
                    Duration.ofHours(
                        2
                    )
                ),
                Instant.parse(
                    "2026-09-30T12:00:00Z"
                ),
                1,
                Optional.of(
                    atLocalTime(
                        10,
                        0
                    )
                )
            );

        /*
         * O snapshot dizia zero ocupados, mas uma reserva de 10:00
         * apareceu depois.
         *
         * A continuidade temporal parte do dado persistido mais
         * recente e o próximo slot é 12:00.
         */
        assertEquals(
            List.of(
                atLocalTime(
                    12,
                    0
                )
            ),
            plan.availableAtSlots()
        );
    }

    @Test
    void shouldLimitPlanByRemainingQuota() {

        PublicationCadencePlan plan =
            planner.plan(
                quota(
                    7,
                    5L
                ),
                cadence(
                    Duration.ofHours(
                        2
                    )
                ),
                Instant.parse(
                    "2026-09-30T15:00:00Z"
                ),
                5,
                Optional.of(
                    atLocalTime(
                        16,
                        0
                    )
                )
            );

        assertEquals(
            2,
            plan.quotaEligibleSlots()
        );

        assertEquals(
            3,
            plan.quotaRejectedSlots()
        );

        assertEquals(
            List.of(
                atLocalTime(
                    18,
                    0
                ),
                atLocalTime(
                    20,
                    0
                )
            ),
            plan.availableAtSlots()
        );
    }

    @Test
    void shouldReportWhenWindowHasFewerSlotsThanQuota() {

        PublicationCadencePlan plan =
            planner.plan(
                quota(
                    7,
                    1L
                ),
                cadence(
                    Duration.ofHours(
                        2
                    )
                ),
                Instant.parse(
                    "2026-09-30T23:30:00Z"
                ),
                3,
                Optional.of(
                    atLocalTime(
                        20,
                        0
                    )
                )
            );

        assertEquals(
            List.of(
                atLocalTime(
                    22,
                    0
                )
            ),
            plan.availableAtSlots()
        );

        assertEquals(
            3,
            plan.quotaEligibleSlots()
        );

        assertEquals(
            1,
            plan.plannedSlots()
        );

        assertEquals(
            2,
            plan.cadenceUnavailableSlots()
        );

        assertFalse(
            plan.fullyPlanned()
        );
    }

    @Test
    void shouldReturnNoSlotsAfterWindowEnd() {

        PublicationCadencePlan plan =
            planner.plan(
                quota(
                    7,
                    0L
                ),
                cadence(
                    Duration.ofHours(
                        2
                    )
                ),
                Instant.parse(
                    "2026-10-01T02:00:00Z"
                ),
                3,
                Optional.empty()
            );

        assertTrue(
            plan.availableAtSlots()
                .isEmpty()
        );

        assertEquals(
            3,
            plan.cadenceUnavailableSlots()
        );
    }

    @Test
    void shouldRequireLastReservationWhenSnapshotAlreadyObservedOccupiedSlots() {

        assertThrows(
            IllegalStateException.class,
            () ->
                planner.plan(
                    quota(
                        7,
                        1L
                    ),
                    cadence(
                        Duration.ofHours(
                            2
                        )
                    ),
                    Instant.parse(
                        "2026-09-30T12:00:00Z"
                    ),
                    1,
                    Optional.empty()
                )
        );
    }

    @Test
    void shouldRejectLastReservationFromDifferentQuotaDate() {

        assertThrows(
            IllegalStateException.class,
            () ->
                planner.plan(
                    quota(
                        7,
                        1L
                    ),
                    cadence(
                        Duration.ofHours(
                            2
                        )
                    ),
                    Instant.parse(
                        "2026-09-30T12:00:00Z"
                    ),
                    1,
                    Optional.of(
                        OffsetDateTime.parse(
                            "2026-09-29T20:00:00-03:00"
                        )
                    )
                )
        );
    }

    @Test
    void shouldReturnEmptyPlanWhenQuotaIsAlreadyExhausted() {

        PublicationCadencePlan plan =
            planner.plan(
                quota(
                    7,
                    7L
                ),
                cadence(
                    Duration.ofHours(
                        2
                    )
                ),
                Instant.parse(
                    "2026-09-30T12:00:00Z"
                ),
                3,
                Optional.empty()
            );

        assertEquals(
            0,
            plan.quotaEligibleSlots()
        );

        assertEquals(
            3,
            plan.quotaRejectedSlots()
        );

        assertEquals(
            0,
            plan.plannedSlots()
        );
    }

    private PublicationQuotaSnapshot quota(
        int maximum,
        long occupied
    ) {

        return new PublicationQuotaSnapshot(
            "TELEGRAM",
            "@public_channel",
            QUOTA_DATE,
            "QUOTA_TEST_V1",
            maximum,
            occupied
        );
    }

    private PublicationCadenceProfile cadence(
        Duration interval
    ) {

        return new PublicationCadenceProfile(
            "CADENCE_TEST_V1",
            interval,
            LocalTime.of(
                8,
                0
            ),
            LocalTime.of(
                22,
                0
            ),
            ZONE
        );
    }

    private OffsetDateTime atLocalTime(
        int hour,
        int minute
    ) {

        return QUOTA_DATE
            .atTime(
                hour,
                minute
            )
            .atZone(
                ZONE
            )
            .toOffsetDateTime();
    }
}
