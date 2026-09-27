package com.raspingamazon.application.enrichment.contract;

import com.raspingamazon.application.observability.OperationalLogContext;
import com.raspingamazon.application.parsing.contract.ParsedDeal;

import java.util.Objects;

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
 *
 * <p>O método histórico de um argumento permanece como a única operação
 * abstrata. A sobrecarga contextual permite transportar identidades
 * operacionais já conhecidas pela orquestração sem alterar a semântica
 * funcional nem quebrar consumidores existentes baseados em lambda.</p>
 */
public interface ProductEnrichmentClient {

    /**
     * Enriquece uma oferta identificada anteriormente.
     *
     * @param deal oferta normalizada pela FASE 6
     * @return resultado normalizado do enriquecimento
     */
    ProductEnrichmentResult enrich(
        ParsedDeal deal
    );

    /**
     * Enriquece a oferta carregando correlação operacional.
     *
     * <p>Implementações que não produzem observabilidade podem utilizar
     * o comportamento default. Nesse caso o contexto é validado e a
     * execução é delegada para o método histórico.</p>
     *
     * @param deal oferta normalizada pela FASE 6
     * @param context correlação operacional conhecida pelo chamador
     * @return resultado normalizado do enriquecimento
     */
    default ProductEnrichmentResult enrich(
        ParsedDeal deal,
        OperationalLogContext context
    ) {

        Objects.requireNonNull(
            context,
            "Operational log context must not be null"
        );

        return enrich(
            deal
        );
    }
}
