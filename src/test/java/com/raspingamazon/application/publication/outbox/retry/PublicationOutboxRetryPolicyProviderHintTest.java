package com.raspingamazon.application.publication.outbox.retry;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicationOutboxRetryPolicyProviderHintTest {

    private static final OffsetDateTime COMPLETED_AT =
        OffsetDateTime.parse(
            "2026-10-06T21:00:00Z"
        );

    @Test
    void providerFloorShouldDelayLocallyAuthorizedRetry() {

        PublicationOutboxRetryPolicy policy =
            (
                attemptNumber,
                completedAt
            ) ->
                Optional.of(
                    completedAt.plusSeconds(
                        30
                    )
                );

        OffsetDateTime providerFloor =
            COMPLETED_AT.plusMinutes(
                2
            );

        assertEquals(
            providerFloor,
            policy.nextAttemptAt(
                    1,
                    COMPLETED_AT,
                    Optional.of(
                        providerFloor
                    )
                )
                .orElseThrow()
        );
    }

    @Test
    void providerFloorShouldNeverShortenLocalBackoff() {

        OffsetDateTime localRetryAt =
            COMPLETED_AT.plusMinutes(
                5
            );

        PublicationOutboxRetryPolicy policy =
            (
                attemptNumber,
                completedAt
            ) ->
                Optional.of(
                    localRetryAt
                );

        assertEquals(
            localRetryAt,
            policy.nextAttemptAt(
                    1,
                    COMPLETED_AT,
                    Optional.of(
                        COMPLETED_AT.plusSeconds(
                            1
                        )
                    )
                )
                .orElseThrow()
        );
    }

    @Test
    void providerFloorShouldNotCreateRetryAfterBudgetIsExhausted() {

        PublicationOutboxRetryPolicy policy =
            (
                attemptNumber,
                completedAt
            ) -> Optional.empty();

        assertTrue(
            policy.nextAttemptAt(
                    5,
                    COMPLETED_AT,
                    Optional.of(
                        COMPLETED_AT.plusHours(
                            1
                        )
                    )
                )
                .isEmpty()
        );
    }

    @Test
    void missingProviderFloorShouldPreserveLocalDecision() {

        OffsetDateTime localRetryAt =
            COMPLETED_AT.plusSeconds(
                30
            );

        PublicationOutboxRetryPolicy policy =
            (
                attemptNumber,
                completedAt
            ) -> Optional.of(
                localRetryAt
            );

        assertEquals(
            localRetryAt,
            policy.nextAttemptAt(
                    1,
                    COMPLETED_AT,
                    Optional.empty()
                )
                .orElseThrow()
        );
    }
}
