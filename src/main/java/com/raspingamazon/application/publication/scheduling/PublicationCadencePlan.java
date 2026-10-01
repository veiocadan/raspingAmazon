package com.raspingamazon.application.publication.scheduling;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Resultado imutável do planejamento de slots de publicação
 * para uma quotaDate.
 *
 * <p>requestedSlots representa quantas Publications o chamador
 * gostaria de programar.</p>
 *
 * <p>quotaEligibleSlots representa quantas delas poderiam avançar
 * considerando somente a quota ainda disponível.</p>
 *
 * <p>availableAtSlots representa quantos slots realmente couberam
 * na janela/cadência restante daquele dia.</p>
 */
public record PublicationCadencePlan(
    LocalDate quotaDate,
    String cadenceProfileVersion,
    int requestedSlots,
    int quotaEligibleSlots,
    List<OffsetDateTime> availableAtSlots
) {

    public PublicationCadencePlan {

        Objects.requireNonNull(
            quotaDate,
            "quotaDate must not be null"
        );

        cadenceProfileVersion =
            requireText(
                cadenceProfileVersion,
                "cadenceProfileVersion"
            );

        if (requestedSlots < 0) {

            throw new IllegalArgumentException(
                "requestedSlots must not be negative"
            );
        }

        if (quotaEligibleSlots < 0
            || quotaEligibleSlots > requestedSlots) {

            throw new IllegalArgumentException(
                "quotaEligibleSlots must be between zero "
                    + "and requestedSlots"
            );
        }

        Objects.requireNonNull(
            availableAtSlots,
            "availableAtSlots must not be null"
        );

        availableAtSlots =
            List.copyOf(
                availableAtSlots
            );

        if (availableAtSlots.size()
            > quotaEligibleSlots) {

            throw new IllegalArgumentException(
                "planned slots must not exceed quotaEligibleSlots"
            );
        }

        for (OffsetDateTime availableAt
            : availableAtSlots) {

            Objects.requireNonNull(
                availableAt,
                "availableAtSlots must not contain null"
            );
        }
    }

    public int plannedSlots() {

        return availableAtSlots.size();
    }

    /**
     * Quantidade que não avançou exclusivamente por falta
     * de quota.
     */
    public int quotaRejectedSlots() {

        return requestedSlots
            - quotaEligibleSlots;
    }

    /**
     * Quantidade que passou pela quota, mas não encontrou
     * espaço restante na janela de cadência.
     */
    public int cadenceUnavailableSlots() {

        return quotaEligibleSlots
            - plannedSlots();
    }

    public boolean fullyPlanned() {

        return plannedSlots()
            == requestedSlots;
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        String trimmed =
            value.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return trimmed;
    }
}
