package com.raspingamazon.infrastructure.amazon.enrichment;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Conteúdo adquirido de uma página individual de produto.
 *
 * <p>Este objeto representa o resultado da etapa de aquisição da
 * página, antes de qualquer interpretação de seller, delivery ou
 * condições comerciais.</p>
 *
 * <p>A separação é deliberada: a origem do conteúdo pode ser HTTP
 * bruto, navegador renderizado ou outro mecanismo de infraestrutura,
 * enquanto os parsers continuam independentes desse mecanismo.</p>
 *
 * @param requestedUri URI originalmente solicitada
 * @param resolvedUri URI final após eventuais redirecionamentos
 * @param html conteúdo observado pelo provider
 * @param collectedAt instante em que o conteúdo foi adquirido
 */
public record ProductPageContent(
    URI requestedUri,
    URI resolvedUri,
    String html,
    OffsetDateTime collectedAt
) {

    public ProductPageContent {

        Objects.requireNonNull(
            requestedUri,
            "requestedUri must not be null"
        );

        Objects.requireNonNull(
            resolvedUri,
            "resolvedUri must not be null"
        );

        Objects.requireNonNull(
            html,
            "html must not be null"
        );

        Objects.requireNonNull(
            collectedAt,
            "collectedAt must not be null"
        );

        if (html.isBlank()) {
            throw new IllegalArgumentException(
                "html must not be blank"
            );
        }
    }
}
