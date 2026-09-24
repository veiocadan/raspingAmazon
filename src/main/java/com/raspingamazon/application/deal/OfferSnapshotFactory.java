package com.raspingamazon.application.deal;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.commercial.PaymentCondition;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Product;
import com.raspingamazon.domain.shared.Money;
import com.raspingamazon.domain.shared.Percentage;

import java.util.List;
import java.util.Objects;

/**
 * Constrói OfferSnapshot a partir dos fatos observados nas etapas
 * anteriores do pipeline.
 *
 * <p>Responsabilidades:</p>
 *
 * <ul>
 *     <li>transportar dados do ParsedDeal;</li>
 *     <li>transportar seller e delivery do enrichment;</li>
 *     <li>aplicar a precedência de fonte para rating e reviewCount;</li>
 *     <li>converter tipos de aplicação em value objects de domínio;</li>
 *     <li>preservar ausência de dados como null.</li>
 * </ul>
 *
 * <p>Para rating e reviewCount a precedência é independente por campo:</p>
 *
 * <pre>
 * ParsedDeal possui valor
 *     -> preserva /deals
 *
 * ParsedDeal não possui valor
 *     -> utiliza evidência normalizada da página individual, se houver
 *
 * nenhuma fonte possui valor
 *     -> null
 * </pre>
 *
 * <p>Esta classe não faz HTTP, não persiste dados, não aplica
 * elegibilidade, não aplica filtros e não calcula score ou momentum.</p>
 */
public final class OfferSnapshotFactory {

    /**
     * Constrói um snapshot sem condições comerciais adicionais.
     */
    public OfferSnapshot create(
        Product product,
        ParsedDeal parsedDeal,
        ProductEnrichmentResult enrichmentResult
    ) {

        return create(
            product,
            parsedDeal,
            enrichmentResult,
            List.of()
        );
    }

    /**
     * Constrói um snapshot preservando também condições comerciais
     * já observadas anteriormente no pipeline.
     */
    public OfferSnapshot create(
        Product product,
        ParsedDeal parsedDeal,
        ProductEnrichmentResult enrichmentResult,
        List<PaymentCondition> paymentConditions
    ) {

        Objects.requireNonNull(
            product,
            "product must not be null"
        );

        Objects.requireNonNull(
            parsedDeal,
            "parsedDeal must not be null"
        );

        Objects.requireNonNull(
            enrichmentResult,
            "enrichmentResult must not be null"
        );

        Objects.requireNonNull(
            paymentConditions,
            "paymentConditions must not be null"
        );

        validateSameAsin(
            product,
            parsedDeal,
            enrichmentResult
        );

        return new OfferSnapshot(
            null,

            product,

            parsedDeal.collectedAt(),

            toMoney(
                parsedDeal.currentPrice()
            ),

            toMoney(
                parsedDeal.basisPrice()
            ),

            toMoney(
                parsedDeal.previousPrice()
            ),

            toPercentage(
                parsedDeal.soldPercentage()
            ),

            resolveRating(
                parsedDeal,
                enrichmentResult
            ),

            resolveReviewCount(
                parsedDeal,
                enrichmentResult
            ),

            enrichmentResult
                .sellerEvidence()
                .rawValue(),

            enrichmentResult
                .deliveryEvidence()
                .rawValue(),

            enrichmentResult
                .sellerEvidence()
                .sellerType(),

            enrichmentResult
                .deliveryEvidence()
                .deliveryType(),

            parsedDeal.source(),

            paymentConditions
        );
    }

    /**
     * /deals é a fonte primária. A página individual somente preenche
     * ausência explícita.
     */
    private Double resolveRating(
        ParsedDeal parsedDeal,
        ProductEnrichmentResult enrichmentResult
    ) {

        if (parsedDeal.rating() != null) {
            return parsedDeal.rating();
        }

        return enrichmentResult
            .ratingEvidence()
            .rating();
    }

    /**
     * A precedência de reviewCount é independente da precedência de rating.
     */
    private Long resolveReviewCount(
        ParsedDeal parsedDeal,
        ProductEnrichmentResult enrichmentResult
    ) {

        if (parsedDeal.reviewCount() != null) {
            return parsedDeal.reviewCount();
        }

        return enrichmentResult
            .reviewCountEvidence()
            .reviewCount();
    }

    private void validateSameAsin(
        Product product,
        ParsedDeal parsedDeal,
        ProductEnrichmentResult enrichmentResult
    ) {

        String productAsin =
            product.asin().value();

        if (!productAsin.equals(
            parsedDeal.asin()
        )) {

            throw new IllegalArgumentException(
                "Product ASIN and ParsedDeal ASIN must match"
            );
        }

        if (!productAsin.equals(
            enrichmentResult.asin()
        )) {

            throw new IllegalArgumentException(
                "Product ASIN and enrichment ASIN must match"
            );
        }
    }

    /**
     * Converte BigDecimal de aplicação em Money do domínio.
     *
     * <p>null continua null.</p>
     */
    private Money toMoney(
        java.math.BigDecimal value
    ) {

        if (value == null) {
            return null;
        }

        return new Money(
            value
        );
    }

    /**
     * Converte BigDecimal de aplicação em Percentage do domínio.
     *
     * <p>null continua null.</p>
     */
    private Percentage toPercentage(
        java.math.BigDecimal value
    ) {

        if (value == null) {
            return null;
        }

        return new Percentage(
            value
        );
    }
}
