package com.raspingamazon.application.enrichment.contract;

import com.raspingamazon.domain.commercial.PaymentCondition;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Resultado normalizado do enriquecimento de uma oferta.
 *
 * <p>Este contrato representa fatos observados durante o enrichment
 * da página individual do produto.</p>
 *
 * <p>Ele deliberadamente não contém decisões de:</p>
 *
 * <ul>
 *     <li>elegibilidade;</li>
 *     <li>filtros;</li>
 *     <li>score;</li>
 *     <li>publicação.</li>
 * </ul>
 *
 * <p>Seller, delivery, rating e reviewCount são transportados como
 * evidências tipadas. As condições comerciais continuam sendo
 * transportadas como objetos de domínio já normalizados, sem decisão
 * sobre qual condição é melhor.</p>
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
     * Evidência de rating observada na página individual.
     */
    RatingEvidence ratingEvidence,

    /**
     * Evidência de reviewCount observada na página individual.
     */
    ReviewCountEvidence reviewCountEvidence,

    /**
     * Condições comerciais explicitamente observadas na página.
     *
     * <p>Ausência é representada por lista vazia.</p>
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
            ratingEvidence,
            "Enrichment rating evidence must not be null"
        );

        Objects.requireNonNull(
            reviewCountEvidence,
            "Enrichment review-count evidence must not be null"
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
     * Construtor de compatibilidade para consumidores que já fornecem
     * PaymentCondition, mas ainda não fornecem rating/reviewCount.
     *
     * <p>Nenhum valor é inventado. As duas novas evidências permanecem
     * explicitamente indisponíveis.</p>
     */
    public ProductEnrichmentResult(
        String asin,
        SellerEvidence sellerEvidence,
        DeliveryEvidence deliveryEvidence,
        List<PaymentCondition> paymentConditions,
        String source,
        String productUrl,
        OffsetDateTime enrichedAt
    ) {
        this(
            asin,
            sellerEvidence,
            deliveryEvidence,
            RatingEvidence.unavailable(),
            ReviewCountEvidence.unavailable(),
            paymentConditions,
            source,
            productUrl,
            enrichedAt
        );
    }

    /**
     * Construtor de compatibilidade para consumidores que ainda não
     * fornecem condições comerciais nem rating/reviewCount.
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
            RatingEvidence.unavailable(),
            ReviewCountEvidence.unavailable(),
            List.of(),
            source,
            productUrl,
            enrichedAt
        );
    }
}
