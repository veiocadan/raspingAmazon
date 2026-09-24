package com.raspingamazon.application.enrichment.port;

import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;

import java.sql.SQLException;

/**
 * Porta de persistência das evidências produzidas pelo enrichment.
 *
 * <p>A camada de aplicação conhece apenas este contrato. Ela não
 * precisa saber se as evidências serão gravadas via JDBC, PostgreSQL
 * ou qualquer outra tecnologia.</p>
 */
public interface OfferEvidenceRepository {

    /**
     * Persiste todas as evidências relacionadas a um snapshot.
     *
     * <p>O conjunto atual inclui:</p>
     *
     * <ul>
     *     <li>SELLER;</li>
     *     <li>DELIVERY;</li>
     *     <li>RATING;</li>
     *     <li>REVIEW_COUNT.</li>
     * </ul>
     *
     * @param offerSnapshotId identificador do snapshot já persistido
     * @param enrichmentResult resultado completo do enrichment
     * @throws SQLException quando ocorre falha de persistência
     */
    void insert(
        long offerSnapshotId,
        ProductEnrichmentResult enrichmentResult
    ) throws SQLException;
}
