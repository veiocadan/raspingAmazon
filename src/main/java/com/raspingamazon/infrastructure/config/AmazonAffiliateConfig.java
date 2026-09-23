package com.raspingamazon.infrastructure.config;

import java.util.Objects;

/**
 * Configuração necessária para geração de links do
 * Programa de Associados Amazon.
 */
public record AmazonAffiliateConfig(
    String associateTag
) {

    public AmazonAffiliateConfig {

        Objects.requireNonNull(
            associateTag,
            "associateTag must not be null"
        );

        if (associateTag.isBlank()) {
            throw new IllegalArgumentException(
                "associateTag must not be blank"
            );
        }
    }
}
