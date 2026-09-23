package com.raspingamazon.application.publication.affiliate;

import java.util.Objects;

/**
 * Resultado versionado da geração de um link de associado.
 *
 * <p>A versão identifica a estratégia utilizada para produzir
 * o link, permitindo auditoria e evolução futura.</p>
 */
public record AffiliateLink(
    String generatorVersion,
    String url
) {

    public AffiliateLink {

        generatorVersion =
            requireText(
                generatorVersion,
                "generatorVersion must not be blank"
            );

        url =
            requireText(
                url,
                "url must not be blank"
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
}
