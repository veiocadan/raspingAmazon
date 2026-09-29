package com.raspingamazon.domain.publication.selection;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Fotografia auditável da capacidade diária de publicação
 * para um canal e destino.
 *
 * <p>occupiedSlots representa posições já consumidas ou
 * reservadas para o dia consultado.</p>
 *
 * <p>Na implementação persistente da FASE 18, uma posição
 * ocupada corresponderá a trabalho externo que ainda reserva
 * capacidade ou que já foi concluído com sucesso.</p>
 *
 * <p>O snapshot não altera estado e não reserva vagas.</p>
 */
public record PublicationQuotaSnapshot(
    String channel,
    String destination,
    LocalDate quotaDate,
    String quotaProfileVersion,
    int maxPublicationsPerDay,
    long occupiedSlots
) {

    public PublicationQuotaSnapshot {

        channel =
            requireText(
                channel,
                "channel"
            );

        destination =
            requireText(
                destination,
                "destination"
            );

        Objects.requireNonNull(
            quotaDate,
            "quotaDate must not be null"
        );

        quotaProfileVersion =
            requireText(
                quotaProfileVersion,
                "quotaProfileVersion"
            );

        if (maxPublicationsPerDay <= 0) {
            throw new IllegalArgumentException(
                "maxPublicationsPerDay must be positive"
            );
        }

        if (occupiedSlots < 0L) {
            throw new IllegalArgumentException(
                "occupiedSlots must not be negative"
            );
        }
    }

    /**
     * Quantidade de posições ainda disponíveis.
     *
     * <p>O resultado nunca é negativo. Isso é importante porque
     * uma configuração pode ser reduzida durante um dia em que
     * mais posições já estavam ocupadas.</p>
     */
    public int availableSlots() {

        long available =
            (long) maxPublicationsPerDay
                - occupiedSlots;

        if (available <= 0L) {
            return 0;
        }

        return (int) available;
    }

    public boolean hasAvailableSlots() {
        return availableSlots() > 0;
    }

    public boolean exhausted() {
        return availableSlots() == 0;
    }

    private static String requireText(
        String value,
        String fieldName
    ) {

        Objects.requireNonNull(
            value,
            fieldName + " must not be null"
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank"
            );
        }

        return value;
    }
}
