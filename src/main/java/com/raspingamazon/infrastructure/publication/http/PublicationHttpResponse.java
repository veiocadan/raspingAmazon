package com.raspingamazon.infrastructure.publication.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resposta HTTP bruta devolvida ao adapter do canal.
 *
 * <p>A classificação de sucesso, falha transitória, falha
 * permanente ou entrega ambígua não pertence ao transporte.
 * Essa interpretação permanece responsabilidade do adapter
 * concreto do provedor.</p>
 *
 * <p>Os headers de resposta são preservados porque alguns
 * contratos HTTP carregam metadados operacionais relevantes,
 * como {@code Retry-After} em respostas 429.</p>
 *
 * <p>Os nomes dos headers são normalizados para lower-case usando
 * {@link Locale#ROOT}. Os valores permanecem na ordem fornecida
 * pelo transporte.</p>
 */
public record PublicationHttpResponse(
    int statusCode,
    String body,
    Map<String, List<String>> headers
) {

    public PublicationHttpResponse {

        if (statusCode < 100
            || statusCode > 599) {

            throw new IllegalArgumentException(
                "statusCode must be between 100 and 599"
            );
        }

        Objects.requireNonNull(
            body,
            "body must not be null"
        );

        headers =
            immutableHeaders(
                headers
            );
    }

    /**
     * Construtor de compatibilidade para callers que não possuem
     * headers de resposta.
     */
    public PublicationHttpResponse(
        int statusCode,
        String body
    ) {

        this(
            statusCode,
            body,
            Map.of()
        );
    }

    /**
     * Retorna todos os valores de um header de forma
     * case-insensitive.
     */
    public List<String> headerValues(
        String name
    ) {

        String normalizedName =
            normalizeHeaderName(
                name
            );

        return headers.getOrDefault(
            normalizedName,
            List.of()
        );
    }

    /**
     * Retorna o primeiro valor do header, quando presente.
     */
    public Optional<String> firstHeaderValue(
        String name
    ) {

        List<String> values =
            headerValues(
                name
            );

        if (values.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
            values.getFirst()
        );
    }

    private static Map<String, List<String>> immutableHeaders(
        Map<String, List<String>> source
    ) {

        Objects.requireNonNull(
            source,
            "headers must not be null"
        );

        Map<String, List<String>> normalized =
            new LinkedHashMap<>();

        source.forEach(
            (
                rawName,
                rawValues
            ) -> {

                String name =
                    normalizeHeaderName(
                        rawName
                    );

                Objects.requireNonNull(
                    rawValues,
                    "header values must not be null"
                );

                List<String> values =
                    normalized.computeIfAbsent(
                        name,
                        ignored ->
                            new ArrayList<>()
                    );

                for (String value :
                    rawValues) {

                    values.add(
                        Objects.requireNonNull(
                            value,
                            "header value must not be null"
                        )
                    );
                }
            }
        );

        Map<String, List<String>> immutable =
            new LinkedHashMap<>();

        normalized.forEach(
            (
                name,
                values
            ) ->
                immutable.put(
                    name,
                    List.copyOf(
                        values
                    )
                )
        );

        return Collections.unmodifiableMap(
            immutable
        );
    }

    private static String normalizeHeaderName(
        String name
    ) {

        Objects.requireNonNull(
            name,
            "header name must not be null"
        );

        String normalized =
            name.trim();

        if (normalized.isEmpty()) {

            throw new IllegalArgumentException(
                "header name must not be blank"
            );
        }

        return normalized.toLowerCase(
            Locale.ROOT
        );
    }
}
