package com.raspingamazon.infrastructure.config;

import com.raspingamazon.domain.publication.scheduling.PublicationCadenceProfile;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * Carrega do ambiente o estado operacional desejado da cadência
 * de publicação.
 */
public final class PublicationCadenceEnvironmentConfigProvider {

    public static final String VERSION_ENV =
        "PUBLICATION_CADENCE_VERSION";

    public static final String INTERVAL_ENV =
        "PUBLICATION_CADENCE_INTERVAL";

    public static final String WINDOW_START_ENV =
        "PUBLICATION_CADENCE_WINDOW_START";

    public static final String WINDOW_END_ENV =
        "PUBLICATION_CADENCE_WINDOW_END";

    public static final String ZONE_ENV =
        "PUBLICATION_CADENCE_ZONE";

    private PublicationCadenceEnvironmentConfigProvider() {
    }

    public static PublicationCadenceProfile load() {

        return load(
            System.getenv()
        );
    }

    /**
     * Variante injetável para testes.
     */
    public static PublicationCadenceProfile load(
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

        Duration interval =
            duration(
                required(
                    environment,
                    INTERVAL_ENV
                ),
                INTERVAL_ENV
            );

        LocalTime windowStart =
            localTime(
                required(
                    environment,
                    WINDOW_START_ENV
                ),
                WINDOW_START_ENV
            );

        LocalTime windowEnd =
            localTime(
                required(
                    environment,
                    WINDOW_END_ENV
                ),
                WINDOW_END_ENV
            );

        ZoneId zone =
            zoneId(
                required(
                    environment,
                    ZONE_ENV
                ),
                ZONE_ENV
            );

        return new PublicationCadenceProfile(
            version,
            interval,
            windowStart,
            windowEnd,
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

        if (duration.isZero()
            || duration.isNegative()) {

            throw new IllegalStateException(
                "Environment variable "
                    + key
                    + " must contain a positive duration"
            );
        }

        return duration;
    }

    private static LocalTime localTime(
        String value,
        String key
    ) {

        try {

            return LocalTime.parse(
                value
            );

        } catch (DateTimeParseException exception) {

            throw new IllegalStateException(
                "Environment variable "
                    + key
                    + " must contain a valid time such as 08:00",
                exception
            );
        }
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
