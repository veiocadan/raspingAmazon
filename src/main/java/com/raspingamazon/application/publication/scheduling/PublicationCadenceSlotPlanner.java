package com.raspingamazon.application.publication.scheduling;

import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Política pura de planejamento de availableAt.
 *
 * <p>O planner não consulta banco e não reserva quota.</p>
 *
 * <p>Ele recebe:</p>
 *
 * <ul>
 *     <li>snapshot da quota observado durante a seleção;</li>
 *     <li>perfil de cadência observado;</li>
 *     <li>instante atual;</li>
 *     <li>quantidade desejada;</li>
 *     <li>último availableAt persistido, quando houver.</li>
 * </ul>
 *
 * <p>O snapshot de quota pode ficar naturalmente defasado entre
 * seleção e dispatch. Portanto é legítimo que ele informe zero
 * posições ocupadas e, posteriormente, a consulta da outbox já
 * encontre uma reserva criada concorrentemente.</p>
 *
 * <p>O inverso não é esperado: se o snapshot já observou posições
 * ocupadas, deve existir ao menos uma reserva primária persistida
 * para que a continuidade temporal possa ser determinada.</p>
 *
 * <p>Os slots são sempre alinhados à grade iniciada em
 * windowStart.</p>
 */
public final class PublicationCadenceSlotPlanner {

    public PublicationCadencePlan plan(
        PublicationQuotaSnapshot quotaSnapshot,
        PublicationCadenceProfile cadenceProfile,
        Instant currentTime,
        int requestedSlots,
        Optional<OffsetDateTime> lastReservedAvailableAt
    ) {

        Objects.requireNonNull(
            quotaSnapshot,
            "quotaSnapshot must not be null"
        );

        Objects.requireNonNull(
            cadenceProfile,
            "cadenceProfile must not be null"
        );

        Objects.requireNonNull(
            currentTime,
            "currentTime must not be null"
        );

        Objects.requireNonNull(
            lastReservedAvailableAt,
            "lastReservedAvailableAt must not be null"
        );

        if (requestedSlots < 0) {

            throw new IllegalArgumentException(
                "requestedSlots must not be negative"
            );
        }

        int quotaEligibleSlots =
            Math.min(
                requestedSlots,
                quotaSnapshot.availableSlots()
            );

        if (quotaEligibleSlots == 0) {

            return emptyPlan(
                quotaSnapshot,
                cadenceProfile,
                requestedSlots,
                0
            );
        }

        validateReservationHistory(
            quotaSnapshot,
            cadenceProfile,
            lastReservedAvailableAt
        );

        ZonedDateTime windowStart =
            quotaSnapshot.quotaDate()
                .atTime(
                    cadenceProfile.windowStart()
                )
                .atZone(
                    cadenceProfile.zone()
                );

        ZonedDateTime windowEnd =
            quotaSnapshot.quotaDate()
                .atTime(
                    cadenceProfile.windowEnd()
                )
                .atZone(
                    cadenceProfile.zone()
                );

        Instant windowStartInstant =
            windowStart.toInstant();

        Instant windowEndInstant =
            windowEnd.toInstant();

        if (currentTime.isAfter(
            windowEndInstant
        )) {

            return emptyPlan(
                quotaSnapshot,
                cadenceProfile,
                requestedSlots,
                quotaEligibleSlots
            );
        }

        Instant threshold =
            later(
                currentTime,
                windowStartInstant
            );

        if (lastReservedAvailableAt.isPresent()) {

            Instant afterLastReservation =
                lastReservedAvailableAt
                    .orElseThrow()
                    .toInstant()
                    .plus(
                        cadenceProfile.interval()
                    );

            threshold =
                later(
                    threshold,
                    afterLastReservation
                );
        }

        if (threshold.isAfter(
            windowEndInstant
        )) {

            return emptyPlan(
                quotaSnapshot,
                cadenceProfile,
                requestedSlots,
                quotaEligibleSlots
            );
        }

        Instant firstSlot =
            alignToCadenceGrid(
                windowStartInstant,
                threshold,
                cadenceProfile.interval()
            );

        if (firstSlot.isAfter(
            windowEndInstant
        )) {

            return emptyPlan(
                quotaSnapshot,
                cadenceProfile,
                requestedSlots,
                quotaEligibleSlots
            );
        }

        List<OffsetDateTime> slots =
            new ArrayList<>(
                quotaEligibleSlots
            );

        Instant candidate =
            firstSlot;

        while (slots.size()
            < quotaEligibleSlots) {

            if (candidate.isAfter(
                windowEndInstant
            )) {

                break;
            }

            slots.add(
                candidate
                    .atZone(
                        cadenceProfile.zone()
                    )
                    .toOffsetDateTime()
            );

            candidate =
                candidate.plus(
                    cadenceProfile.interval()
                );
        }

        return new PublicationCadencePlan(
            quotaSnapshot.quotaDate(),
            cadenceProfile.version(),
            requestedSlots,
            quotaEligibleSlots,
            slots
        );
    }

    private void validateReservationHistory(
        PublicationQuotaSnapshot quotaSnapshot,
        PublicationCadenceProfile cadenceProfile,
        Optional<OffsetDateTime> lastReservedAvailableAt
    ) {

        if (quotaSnapshot.occupiedSlots() > 0L
            && lastReservedAvailableAt.isEmpty()) {

            throw new IllegalStateException(
                "lastReservedAvailableAt is required when "
                    + "quotaSnapshot contains occupied slots"
            );
        }

        /*
         * Não rejeitamos:
         *
         * occupiedSlots == 0
         * +
         * lastReservedAvailableAt presente
         *
         * porque outra execução pode ter reservado uma posição
         * depois que a SelectionRun capturou seu quotaSnapshot.
         */

        if (lastReservedAvailableAt.isEmpty()) {

            return;
        }

        OffsetDateTime lastReserved =
            lastReservedAvailableAt
                .orElseThrow();

        if (!lastReserved
            .toInstant()
            .atZone(
                cadenceProfile.zone()
            )
            .toLocalDate()
            .equals(
                quotaSnapshot.quotaDate()
            )) {

            throw new IllegalStateException(
                "lastReservedAvailableAt must belong to "
                    + "quotaSnapshot quotaDate"
            );
        }
    }

    private Instant alignToCadenceGrid(
        Instant windowStart,
        Instant threshold,
        Duration interval
    ) {

        if (!threshold.isAfter(
            windowStart
        )) {

            return windowStart;
        }

        long intervalSeconds =
            interval.toSeconds();

        Duration elapsed =
            Duration.between(
                windowStart,
                threshold
            );

        long elapsedSeconds =
            elapsed.getSeconds();

        long completedIntervals =
            elapsedSeconds
                / intervalSeconds;

        Instant candidate =
            windowStart.plusSeconds(
                completedIntervals
                    * intervalSeconds
            );

        if (candidate.isBefore(
            threshold
        )) {

            candidate =
                candidate.plusSeconds(
                    intervalSeconds
                );
        }

        return candidate;
    }

    private Instant later(
        Instant first,
        Instant second
    ) {

        if (first.isAfter(
            second
        )) {

            return first;
        }

        return second;
    }

    private PublicationCadencePlan emptyPlan(
        PublicationQuotaSnapshot quotaSnapshot,
        PublicationCadenceProfile cadenceProfile,
        int requestedSlots,
        int quotaEligibleSlots
    ) {

        return new PublicationCadencePlan(
            quotaSnapshot.quotaDate(),
            cadenceProfile.version(),
            requestedSlots,
            quotaEligibleSlots,
            List.of()
        );
    }
}
