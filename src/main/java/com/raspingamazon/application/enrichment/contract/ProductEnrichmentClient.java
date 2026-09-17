package com.raspingamazon.application.enrichment.contract;

import com.raspingamazon.application.parsing.contract.ParsedDeal;

/**
 * Contrato responsável por enriquecer uma oferta já identificada.
 *
 * <p>A implementação recebe um ParsedDeal produzido pela FASE 6
 * e busca informações adicionais sobre a oferta.</p>
 *
 * <p>O contrato não conhece HTML, HTTP, Amazon, PostgreSQL ou
 * qualquer tecnologia específica.</p>
 *
 * <p>O enriquecimento produz evidências normalizadas, mas não
 * toma a decisão final de elegibilidade.</p>
 */
public interface ProductEnrichmentClient {

    /**
     * Enriquece uma oferta identificada anteriormente.
     *
     * @param deal oferta normalizada pela FASE 6
     * @return resultado normalizado do enriquecimento
     */
    ProductEnrichmentResult enrich(ParsedDeal deal);
}