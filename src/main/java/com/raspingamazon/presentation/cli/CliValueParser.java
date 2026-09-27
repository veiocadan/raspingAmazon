package com.raspingamazon.presentation.cli;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Conversores sintáticos da CLI.
 *
 * <p>Esta classe não aplica regra comercial. Ela apenas converte
 * texto recebido pelo terminal em tipos Java e transforma erros
 * sintáticos em CliUsageException.</p>
 */
public final class CliValueParser {

    private CliValueParser() {
    }

    public static boolean strictBoolean(
        String option,
        String value
    ) {

        if ("true".equalsIgnoreCase(
            value
        )) {

            return true;
        }

        if ("false".equalsIgnoreCase(
            value
        )) {

            return false;
        }

        throw new CliUsageException(
            option
                + " must be true or false"
        );
    }

    public static long positiveLong(
        String option,
        String value
    ) {

        final long parsed;

        try {

            parsed =
                Long.parseLong(
                    value
                );

        } catch (NumberFormatException exception) {

            throw new CliUsageException(
                option
                    + " must be a positive integer"
            );
        }

        if (parsed <= 0L) {
            throw new CliUsageException(
                option
                    + " must be a positive integer"
            );
        }

        return parsed;
    }

    public static int positiveInt(
        String option,
        String value
    ) {

        final int parsed;

        try {

            parsed =
                Integer.parseInt(
                    value
                );

        } catch (NumberFormatException exception) {

            throw new CliUsageException(
                option
                    + " must be a positive integer"
            );
        }

        if (parsed <= 0) {
            throw new CliUsageException(
                option
                    + " must be a positive integer"
            );
        }

        return parsed;
    }

    public static BigDecimal decimal(
        String option,
        String value
    ) {

        try {

            return new BigDecimal(
                value
            );

        } catch (NumberFormatException exception) {

            throw new CliUsageException(
                option
                    + " must be a decimal number"
            );
        }
    }

    public static OffsetDateTime offsetDateTime(
        String option,
        String value
    ) {

        try {

            return OffsetDateTime.parse(
                value
            );

        } catch (DateTimeParseException exception) {

            throw new CliUsageException(
                option
                    + " must be an ISO-8601 offset date-time"
            );
        }
    }

    /**
     * Converte uma duração ISO-8601 estritamente positiva.
     *
     * <p>Exemplos válidos:</p>
     *
     * <pre>
     * PT30S
     * PT15M
     * PT2H
     * P1D
     * </pre>
     */
    public static Duration positiveDuration(
        String option,
        String value
    ) {

        final Duration parsed;

        try {

            parsed =
                Duration.parse(
                    value
                );

        } catch (DateTimeParseException exception) {

            throw new CliUsageException(
                option
                    + " must be a positive ISO-8601 duration"
            );
        }

        if (parsed.isZero()
            || parsed.isNegative()) {

            throw new CliUsageException(
                option
                    + " must be a positive ISO-8601 duration"
            );
        }

        return parsed;
    }
}
