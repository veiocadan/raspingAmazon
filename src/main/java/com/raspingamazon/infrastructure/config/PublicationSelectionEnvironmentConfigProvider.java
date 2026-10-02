package com.raspingamazon.infrastructure.config;

import com.raspingamazon.domain.publication.selection.PublicationSelectionPolicy;
import com.raspingamazon.domain.publication.selection.PublicationSelectionProfile;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * Carrega do ambiente a configuração temporal desejada
 * da seleção operacional de publicações.
 *
 * <p>A versão configurada identifica uma revisão imutável da
 * configuração pertencente à política atualmente implementada.</p>
 *
 * <p>Exemplo:</p>
 *
 * <pre>
 * PUBLICATION_SELECTION_VERSION=
 *     PUBLICATION_SELECTION_V1_CONFIG_V1
 *
 * PUBLICATION_SELECTION_HARD_COOLDOWN=PT48H
 * PUBLICATION_SELECTION_PREFERRED_COOLDOWN=PT168H
 * </pre>
 *
 * <p>Uma mudança apenas nos valores operacionais deve criar uma
 * nova revisão:</p>
 *
 * <pre>
 * PUBLICATION_SELECTION_V1_CONFIG_V2
 * </pre>
 *
 * <p>sem alterar a versão semântica do algoritmo
 * PUBLICATION_SELECTION_V1.</p>
 */
public final class PublicationSelectionEnvironmentConfigProvider {

    public static final String VERSION_ENV =
        "PUBLICATION_SELECTION_VERSION";

    public static final String HARD_COOLDOWN_ENV =
        "PUBLICATION_SELECTION_HARD_COOLDOWN";

    public static final String PREFERRED_COOLDOWN_ENV =
        "PUBLICATION_SELECTION_PREFERRED_COOLDOWN";

    private PublicationSelectionEnvironmentConfigProvider() {
    }

    public static PublicationSelectionProfile load() {

        return load(
            System.getenv()
        );
    }

    /**
     * Variante determinística para testes.
     */
    public static PublicationSelectionProfile load(
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

        if (!PublicationSelectionPolicy
            .supportsProfileVersion(
                version
            )) {

            throw new IllegalStateException(
                "Environment variable "
                    + VERSION_ENV
                    + " must identify a configuration compatible "
                    + "with policy "
                    + PublicationSelectionPolicy.VERSION
                    + ". Expected "
                    + PublicationSelectionPolicy
                    .CONFIGURATION_VERSION_PREFIX
                    + "<positive integer>"
            );
        }

        Duration hardCooldown =
            duration(
                required(
                    environment,
                    HARD_COOLDOWN_ENV
                ),
                HARD_COOLDOWN_ENV
            );

        Duration preferredCooldown =
            duration(
                required(
                    environment,
                    PREFERRED_COOLDOWN_ENV
                ),
                PREFERRED_COOLDOWN_ENV
            );

        if (preferredCooldown.compareTo(
            hardCooldown
        ) < 0) {

            throw new IllegalStateException(
                "Environment variable "
                    + PREFERRED_COOLDOWN_ENV
                    + " must be greater than or equal to "
                    + HARD_COOLDOWN_ENV
            );
        }

        return new PublicationSelectionProfile(
            version,
            hardCooldown,
            preferredCooldown
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

    private static Duration duration(
        String value,
        String key
    ) {

        final Duration duration;

        try {

            duration =
                Duration.parse(
                    value
                );

        } catch (DateTimeParseException exception) {

            throw new IllegalStateException(
                "Environment variable "
                    + key
                    + " must contain an ISO-8601 duration",
                exception
            );
        }

        if (duration.isNegative()) {

            throw new IllegalStateException(
                "Environment variable "
                    + key
                    + " must not be negative"
            );
        }

        /*
         * O contrato persistente trabalha em segundos inteiros.
         */
        if (duration.getNano() != 0) {

            throw new IllegalStateException(
                "Environment variable "
                    + key
                    + " must use whole seconds"
            );
        }

        return duration;
    }
}
