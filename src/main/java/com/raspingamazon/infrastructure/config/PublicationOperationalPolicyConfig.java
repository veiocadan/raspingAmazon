package com.raspingamazon.infrastructure.config;

import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;

import java.util.Objects;

/**
 * Estado operacional desejado das políticas de publicação
 * para um único escopo:
 *
 * <pre>
 * channel + destination
 * </pre>
 *
 * <p>Quota e cadência continuam sendo conceitos independentes,
 * porém são ativadas atomicamente para evitar configuração
 * parcialmente aplicada.</p>
 */
public record PublicationOperationalPolicyConfig(
    String channel,
    String destination,
    PublicationQuotaProfile quotaProfile,
    PublicationCadenceProfile cadenceProfile
) {

    public PublicationOperationalPolicyConfig {

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
            quotaProfile,
            "quotaProfile must not be null"
        );

        Objects.requireNonNull(
            cadenceProfile,
            "cadenceProfile must not be null"
        );

        /*
         * Para um mesmo escopo operacional, o conceito de dia da
         * quota e a janela da cadência devem utilizar a mesma zona.
         *
         * Evita, por exemplo:
         *
         * quota       = America/Sao_Paulo
         * cadência    = UTC
         *
         * e uma publicação atravessar duas interpretações
         * diferentes do mesmo dia operacional.
         */
        if (!quotaProfile.quotaZone()
            .equals(
                cadenceProfile.zone()
            )) {

            throw new IllegalArgumentException(
                "quotaProfile and cadenceProfile must use "
                    + "the same operational zone"
            );
        }
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
