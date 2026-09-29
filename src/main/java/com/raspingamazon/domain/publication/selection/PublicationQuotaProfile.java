package com.raspingamazon.domain.publication.selection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Configuração versionada da quota operacional de publicação.
 *
 * <p>A quota limita quantas posições de publicação podem ser
 * ocupadas por dia dentro de um canal e destino.</p>
 *
 * <p>O timezone faz parte explícita da configuração porque o
 * conceito de "dia" depende de uma zona temporal.</p>
 *
 * <p>Esta configuração não define horário de execução. Cadência,
 * frequência e pausa continuam pertencendo ao scheduler.</p>
 */
public record PublicationQuotaProfile(
    String version,
    int maxPublicationsPerDay,
    ZoneId quotaZone
) {

    public PublicationQuotaProfile {

        version =
            requireText(
                version,
                "version"
            );

        if (maxPublicationsPerDay <= 0) {
            throw new IllegalArgumentException(
                "maxPublicationsPerDay must be positive"
            );
        }

        Objects.requireNonNull(
            quotaZone,
            "quotaZone must not be null"
        );
    }

    /**
     * Determina de forma reproduzível a data de quota aplicável
     * a determinado instante.
     *
     * @param currentTime instante lógico da operação
     * @return data local da quota no timezone configurado
     */
    public LocalDate quotaDateAt(
        Instant currentTime
    ) {

        Objects.requireNonNull(
            currentTime,
            "currentTime must not be null"
        );

        return currentTime
            .atZone(
                quotaZone
            )
            .toLocalDate();
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
