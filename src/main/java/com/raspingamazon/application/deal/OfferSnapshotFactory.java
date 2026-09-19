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
 *     <li>converter tipos de aplicação em value objects de domínio;</li>
 *     <li>preservar ausência de dados como null.</li>
 * </ul>
 *
 * <p>Esta classe não:</p>
 *
 * <ul>
 *     <li>faz HTTP;</li>
 *     <li>persiste dados;</li>
 *     <li>aplica elegibilidade;</li>
 *     <li>aplica filtros;</li>
 *     <li>calcula score ou momentum.</li>
 * </ul>
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

        /*
         * O snapshot só pode combinar informações do mesmo ASIN.
         *
         * Isso evita montar uma oferta usando o produto A e evidências
         * de seller/delivery do produto B.
         */
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

                /*
                 * D3:
                 * rating e reviewCount agora atravessam explicitamente
                 * ParsedDeal -> OfferSnapshot.
                 */
                parsedDeal.rating(),

                parsedDeal.reviewCount(),

                /*
                 * O valor textual bruto continua preservado no snapshot
                 * para compatibilidade e leitura humana.
                 */
                enrichmentResult
                        .sellerEvidence()
                        .rawValue(),

                enrichmentResult
                        .deliveryEvidence()
                        .rawValue(),

                /*
                 * A classificação normalizada é utilizada pelas regras
                 * estruturais de elegibilidade.
                 */
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