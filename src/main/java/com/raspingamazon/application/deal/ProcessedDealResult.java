package com.raspingamazon.application.deal;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.parsing.contract.ParsedDeal;
import com.raspingamazon.domain.deal.OfferSnapshot;
import com.raspingamazon.domain.product.Product;

import java.util.Objects;

/**
 * Resultado de uma oferta que atravessou o fluxo vertical
 * síncrono da aplicação.
 *
 * <p>Este objeto é principalmente útil para observabilidade,
 * testes e futuras integrações.</p>
 */
public record ProcessedDealResult(
        ParsedDeal parsedDeal,
        ProductEnrichmentResult enrichmentResult,
        Product product,
        OfferSnapshot offerSnapshot
) {

    public ProcessedDealResult {

        Objects.requireNonNull(
                parsedDeal,
                "parsedDeal must not be null"
        );

        Objects.requireNonNull(
                enrichmentResult,
                "enrichmentResult must not be null"
        );

        Objects.requireNonNull(
                product,
                "product must not be null"
        );

        Objects.requireNonNull(
                offerSnapshot,
                "offerSnapshot must not be null"
        );

        /*
         * Ao final do fluxo, Product e OfferSnapshot precisam
         * obrigatoriamente estar persistidos.
         */
        if (product.id() == null) {
            throw new IllegalArgumentException(
                    "Processed product must have an id"
            );
        }

        if (offerSnapshot.id() == null) {
            throw new IllegalArgumentException(
                    "Processed offerSnapshot must have an id"
            );
        }
    }
}