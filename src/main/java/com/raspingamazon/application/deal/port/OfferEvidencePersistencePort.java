package com.raspingamazon.application.deal.port;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;

/**
 * Porta de persistência das evidências observadas durante
 * o enriquecimento da página de produto.
 *
 * <p>A camada de aplicação trabalha somente com este contrato.
 * SQLException, Connection, PreparedStatement e detalhes de schema
 * pertencem à infraestrutura.</p>
 */
public interface OfferEvidencePersistencePort {

    /**
     * Persiste as evidências associadas a um snapshot já persistido.
     *
     * @param offerSnapshotId identificador persistente do snapshot
     * @param enrichmentResult evidências obtidas no enrichment
     */
    void save(
            long offerSnapshotId,
            ProductEnrichmentResult enrichmentResult
    );
}