package com.raspingamazon.infrastructure.publication.http;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Interpreta o header HTTP {@code Retry-After}.
 *
 * <p>São aceitas as duas formas previstas pelo contrato HTTP:</p>
 *
 * <ul>
 *     <li>delay-seconds: quantidade inteira não negativa de segundos;</li>
 *     <li>HTTP-date: data absoluta em formato RFC 1123.</li>
 * </ul>
 *
 * <p>Header ausente ou inválido não transforma a resposta em outro
 * tipo de falha. O caller pode simplesmente aplicar sua política
 * local de backoff.</p>
 *
 * <p>Quando mais de um valor válido é recebido, o instante mais
 * conservador (mais distante) vence. Isso impede que respostas
 * duplicadas/inconsistentes enfraqueçam o limite do provedor.</p>
 */
public final class PublicationRetryAfterParser {

    private static final String RETRY_AFTER =
        "Retry-After";

    private static final Pattern DELAY_SECONDS =
        Pattern.compile(
            "[0-9]+"
        );

    public Optional<OffsetDateTime> retryNotBefore(
        PublicationHttpResponse response,
        OffsetDateTime observedAt
    ) {

        PublicationHttpResponse validatedResponse =
            Objects.requireNonNull(
                response,
                "response must not be null"
            );

        OffsetDateTime validatedObservedAt =
            Objects.requireNonNull(
                observedAt,
                "observedAt must not be null"
            );

        return validatedResponse
            .headerValues(
                RETRY_AFTER
            )
            .stream()
            .map(
                String::trim
            )
            .filter(
                value ->
                    !value.isEmpty()
            )
            .map(
                value ->
                    parseValue(
                        value,
                        validatedObservedAt
                    )
            )
            .flatMap(
                Optional::stream
            )
            .max(
                Comparator.comparing(
                    OffsetDateTime::toInstant
                )
            );
    }

    private Optional<OffsetDateTime> parseValue(
        String value,
        OffsetDateTime observedAt
    ) {

        if (DELAY_SECONDS.matcher(
            value
        ).matches()) {

            return parseDelaySeconds(
                value,
                observedAt
            );
        }

        return parseHttpDate(
            value,
            observedAt
        );
    }

    private Optional<OffsetDateTime> parseDelaySeconds(
        String value,
        OffsetDateTime observedAt
    ) {

        final long seconds;

        try {

            seconds =
                Long.parseLong(
                    value
                );

        } catch (NumberFormatException exception) {

            return Optional.empty();
        }

        try {

            return Optional.of(
                observedAt.plusSeconds(
                    seconds
                )
            );

        } catch (
            DateTimeException
            | ArithmeticException exception
        ) {

            return Optional.empty();
        }
    }

    private Optional<OffsetDateTime> parseHttpDate(
        String value,
        OffsetDateTime observedAt
    ) {

        final OffsetDateTime parsed;

        try {

            parsed =
                ZonedDateTime.parse(
                    value,
                    DateTimeFormatter.RFC_1123_DATE_TIME
                )
                    .toOffsetDateTime()
                    .withOffsetSameInstant(
                        observedAt.getOffset()
                    );

        } catch (DateTimeParseException exception) {

            return Optional.empty();
        }

        if (parsed.toInstant()
            .isBefore(
                observedAt.toInstant()
            )) {

            return Optional.of(
                observedAt
            );
        }

        return Optional.of(
            parsed
        );
    }
}
