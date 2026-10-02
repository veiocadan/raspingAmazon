package com.raspingamazon.infrastructure.config;

import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;
import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;

import java.util.Map;
import java.util.Objects;

/**
 * Carrega do ambiente a configuração operacional desejada
 * de publicação.
 *
 * <p>Além de quota e cadência, este provider define explicitamente
 * o escopo primário ao qual elas se aplicam:</p>
 *
 * <pre>
 * PUBLICATION_PRIMARY_CHANNEL
 * PUBLICATION_PRIMARY_DESTINATION
 * </pre>
 *
 * <p>O provider somente interpreta e valida configuração.
 * Persistência e ativação permanecem fora desta classe.</p>
 */
public final class
PublicationOperationalPolicyEnvironmentConfigProvider {

    public static final String CHANNEL_ENV =
        "PUBLICATION_PRIMARY_CHANNEL";

    public static final String DESTINATION_ENV =
        "PUBLICATION_PRIMARY_DESTINATION";

    private PublicationOperationalPolicyEnvironmentConfigProvider() {
    }

    public static PublicationOperationalPolicyConfig load() {

        return load(
            System.getenv()
        );
    }

    /**
     * Variante determinística para testes.
     */
    public static PublicationOperationalPolicyConfig load(
        Map<String, String> environment
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        String channel =
            required(
                environment,
                CHANNEL_ENV
            );

        String destination =
            required(
                environment,
                DESTINATION_ENV
            );

        PublicationQuotaProfile quotaProfile =
            PublicationQuotaEnvironmentConfigProvider.load(
                environment
            );

        PublicationCadenceProfile cadenceProfile =
            PublicationCadenceEnvironmentConfigProvider.load(
                environment
            );

        return new PublicationOperationalPolicyConfig(
            channel,
            destination,
            quotaProfile,
            cadenceProfile
        );
    }

    private static String required(
        Map<String, String> environment,
        String key
    ) {

        String value =
            environment.get(
                key
            );

        if (value == null
            || value.isBlank()) {

            throw new IllegalStateException(
                "Required environment variable "
                    + key
                    + " is missing or blank"
            );
        }

        return value.trim();
    }
}
