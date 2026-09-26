package com.raspingamazon.application.operation.publication;

import java.util.Objects;

/**
 * Detalhe operacional completo de uma Publication.
 *
 * <p>PublicationSummary contém a identidade, origem, produto,
 * status e versões de geração. Este read model acrescenta os
 * campos de conteúdo deliberadamente omitidos da listagem.</p>
 *
 * <p>Este objeto é somente leitura e não participa de transições
 * de estado da Publication.</p>
 */
public record PublicationDetail(
    PublicationSummary summary,
    String generatedText,
    String affiliateUrl
) {

    public PublicationDetail {

        Objects.requireNonNull(
            summary,
            "PublicationDetail summary must not be null"
        );

        generatedText =
            requireText(
                generatedText,
                "PublicationDetail generatedText must not be blank"
            );

        affiliateUrl =
            optionalText(
                affiliateUrl,
                "PublicationDetail affiliateUrl must not be blank"
            );
    }

    private static String requireText(
        String value,
        String message
    ) {

        Objects.requireNonNull(
            value,
            message
        );

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }

    private static String optionalText(
        String value,
        String message
    ) {

        if (value == null) {
            return null;
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                message
            );
        }

        return value;
    }
}
