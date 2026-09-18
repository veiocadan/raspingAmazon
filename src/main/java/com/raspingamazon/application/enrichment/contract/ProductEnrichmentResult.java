package com.raspingamazon.application.enrichment.contract;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Resultado normalizado do enriquecimento de uma oferta.
 *
 * <p>Este contrato representa o que foi observado durante o
 * enriquecimento da página individual do produto.</p>
 *
 * <p>Ele deliberadamente não contém uma decisão de elegibilidade.
 * A decisão "seller Amazon + delivery Amazon" pertence à camada de
 * validação e permanece separada do enriquecimento.</p>
 *
 * <p>Seller e delivery são representados por objetos tipados para
 * evitar o problema de vários argumentos String posicionais.</p>
 */
public record ProductEnrichmentResult(

        /**
         * ASIN do produto enriquecido.
         */
        String asin,

        /**
         * Evidência completa do vendedor.
         */
        SellerEvidence sellerEvidence,

        /**
         * Evidência completa da entrega.
         */
        DeliveryEvidence deliveryEvidence,

        /**
         * Adaptador/fonte responsável pelo enriquecimento.
         *
         * <p>Exemplo: {@code AMAZON_PRODUCT_PAGE}.</p>
         */
        String source,

        /**
         * URL efetivamente utilizada para enriquecer o produto.
         */
        String productUrl,

        /**
         * Momento em que o enriquecimento foi realizado.
         */
        OffsetDateTime enrichedAt
) {

    /**
     * Validação dos dados fundamentais do resultado.
     */
    public ProductEnrichmentResult {
        Objects.requireNonNull(
                asin,
                "Enrichment asin must not be null"
        );

        Objects.requireNonNull(
                sellerEvidence,
                "Enrichment seller evidence must not be null"
        );

        Objects.requireNonNull(
                deliveryEvidence,
                "Enrichment delivery evidence must not be null"
        );

        Objects.requireNonNull(
                source,
                "Enrichment source must not be null"
        );

        Objects.requireNonNull(
                productUrl,
                "Enrichment product URL must not be null"
        );

        Objects.requireNonNull(
                enrichedAt,
                "Enrichment timestamp must not be null"
        );

        if (asin.isBlank()) {
            throw new IllegalArgumentException(
                    "Enrichment asin must not be blank"
            );
        }

        if (source.isBlank()) {
            throw new IllegalArgumentException(
                    "Enrichment source must not be blank"
            );
        }

        if (productUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Enrichment product URL must not be blank"
            );
        }
    }
}