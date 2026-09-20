package com.raspingamazon.application.enrichment.contract;

import com.raspingamazon.domain.commercial.PaymentCondition;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Resultado normalizado do enriquecimento de uma oferta.
 *
 * Este contrato representa fatos observados durante o enriquecimento
 * da página individual do produto.
 *
 * Ele deliberadamente não contém decisões de:
 *
 * - elegibilidade;
 * - filtros;
 * - score;
 * - publicação.
 *
 * Seller e delivery são representados por objetos tipados.
 *
 * As condições comerciais são transportadas como objetos de domínio
 * já normalizados, mas sem decisão sobre qual delas é melhor.
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
     * Condições comerciais explicitamente observadas na página.
     *
     * Pode conter, por exemplo:
     *
     * - pagamento à vista via Pix/NuPay;
     * - parcelamento no cartão.
     *
     * Ausência é representada por lista vazia.
     */
    List<PaymentCondition> paymentConditions,

    /**
     * Adaptador/fonte responsável pelo enriquecimento.
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
     * Construtor principal com validação do contrato.
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
            paymentConditions,
            "Enrichment payment conditions must not be null"
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

        paymentConditions =
            List.copyOf(
                paymentConditions
            );
    }

    /**
     * Construtor de compatibilidade para consumidores que ainda não
     * fornecem condições comerciais.
     *
     * Ele preserva o contrato anterior utilizando lista vazia,
     * sem inventar qualquer condição.
     *
     * Isso permite evoluir o pipeline incrementalmente:
     *
     * FASE 9-C1.5-A:
     * enrichment passa a suportar PaymentCondition.
     *
     * FASE 9-C1.5-B:
     * o fluxo vertical passa efetivamente a consumi-las.
     */
    public ProductEnrichmentResult(
        String asin,
        SellerEvidence sellerEvidence,
        DeliveryEvidence deliveryEvidence,
        String source,
        String productUrl,
        OffsetDateTime enrichedAt
    ) {
        this(
            asin,
            sellerEvidence,
            deliveryEvidence,
            List.of(),
            source,
            productUrl,
            enrichedAt
        );
    }
}
