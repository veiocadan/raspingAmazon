package com.raspingamazon.application.enrichment.contract;

import com.raspingamazon.domain.validation.DeliveryType;
import com.raspingamazon.domain.validation.SellerType;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Resultado normalizado do enriquecimento de uma oferta.
 *
 * <p>Este objeto representa evidências obtidas durante o enriquecimento.
 * Ele não representa uma decisão de elegibilidade.</p>
 *
 * <p>Os valores brutos são preservados para permitir rastreabilidade
 * e auditoria. As classificações normalizadas permitem que as próximas
 * fases trabalhem sem depender dos textos específicos apresentados
 * pela Amazon.</p>
 */
public record ProductEnrichmentResult(

        /**
         * ASIN da oferta enriquecida.
         */
        String asin,

        /**
         * Valor original observado para o vendedor.
         */
        String rawSellerValue,

        /**
         * Classificação normalizada do vendedor.
         */
        SellerType sellerType,

        /**
         * Campo/estrutura que forneceu a evidência do vendedor.
         */
        String sellerEvidenceSource,

        /**
         * Valor original observado para a entrega.
         */
        String rawDeliveryValue,

        /**
         * Classificação normalizada da entrega.
         */
        DeliveryType deliveryType,

        /**
         * Campo/estrutura que forneceu a evidência da entrega.
         */
        String deliveryEvidenceSource,

        /**
         * Origem do enriquecimento.
         */
        String source,

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
                sellerType,
                "Enrichment sellerType must not be null"
        );

        Objects.requireNonNull(
                deliveryType,
                "Enrichment deliveryType must not be null"
        );

        Objects.requireNonNull(
                source,
                "Enrichment source must not be null"
        );

        Objects.requireNonNull(
                enrichedAt,
                "Enrichment enrichedAt must not be null"
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
    }
}