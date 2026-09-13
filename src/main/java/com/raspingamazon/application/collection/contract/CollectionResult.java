package com.raspingamazon.application.collection.contract;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Resultado estável produzido por uma etapa de coleta.
 *
 * <p>A coleta entrega o conteúdo bruto e informações mínimas de
 * rastreabilidade. A interpretação desse conteúdo pertence ao parser
 * de uma fase posterior.</p>
 *
 * <p>Este contrato não conhece Amazon, HTML, JSON, banco de dados
 * ou biblioteca HTTP.</p>
 */
public record CollectionResult(
        String content,
        OffsetDateTime collectedAt,
        String source
) {

    public CollectionResult {
        Objects.requireNonNull(
                content,
                "Collection content must not be null"
        );
        Objects.requireNonNull(
                collectedAt,
                "Collected at must not be null"
        );
        Objects.requireNonNull(
                source,
                "Collection source must not be null"
        );

        // Uma coleta sem conteúdo não representa um resultado válido.
        if (content.isBlank()) {
            throw new IllegalArgumentException(
                    "Collection content must not be blank"
            );
        }

        if (source.isBlank()) {
            throw new IllegalArgumentException(
                    "Collection source must not be blank"
            );
        }
    }
}