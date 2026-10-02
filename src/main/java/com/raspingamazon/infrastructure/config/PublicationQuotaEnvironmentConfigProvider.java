package com.raspingamazon.infrastructure.config;

import com.raspingamazon.domain.publication.selection.PublicationQuotaProfile;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

/**
 * Carrega do ambiente o estado operacional desejado da quota
 * de publicação.
 *
 * <p>O ambiente não substitui a persistência histórica.</p>
 *
 * <p>Fluxo esperado:</p>
 *
 * <pre>
 * environment
 *      |
 *      v
 * PublicationQuotaProfile
 *      |
 *      v
 * sincronização PostgreSQL
 *      |
 *      v
 * perfil histórico/versionado
 * </pre>
 */
public final class PublicationQuotaEnvironmentConfigProvider {

    public static final String VERSION_ENV =
        "PUBLICATION_QUOTA_VERSION";

    public static final String MAX_PER_DAY_ENV =
        "PUBLICATION_QUOTA_MAX_PER_DAY";

    public static final String ZONE_ENV =
        "PUBLICATION_QUOTA_ZONE";

    private PublicationQuotaEnvironmentConfigProvider() {
    }

    public static PublicationQuotaProfile load() {

        return load(
            System.getenv()
        );
    }

    /**
     * Variante injetável para testes.
     */
    public static PublicationQuotaProfile load(
        Map<String, String> environment
    ) {

        Objects.requireNonNull(
            environment,
            "environment must not be null"
        );

        String version =
            required(
                environment,
                VERSION_ENV
            );

        int maxPerDay =
            positiveInteger(
                required(
                    environment,
                    MAX_PER_DAY_ENV
                ),
                MAX_PER_DAY_ENV
            );

        ZoneId zone =
            zoneId(
                required(
                    environment,
                    ZONE_ENV
                ),
                ZONE_ENV
            );

        return new PublicationQuotaProfile(
            version,
            maxPerDay,
            zone
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

    private static int positiveInteger(
        String value,
        String key
    ) {

        final int parsed;

        try {

            parsed =
                Integer.parseInt(
                    value
                );

        } catch (NumberFormatException exception) {

            throw new IllegalStateException(
                "Environment variable "
                    + key
                    + " must be a positive integer",
                exception
            );
        }

        if (parsed <= 0) {

            throw new IllegalStateException(
                "Environment variable "
                    + key
                    + " must be a positive integer"
            );
        }

        return parsed;
    }

    private static ZoneId zoneId(
        String value,
        String key
    ) {

        try {

            return ZoneId.of(
                value
            );

        } catch (DateTimeException exception) {

            throw new IllegalStateException(
                "Environment variable "
                    + key
                    + " contains an invalid ZoneId: "
                    + value,
                exception
            );
        }
    }
}
