package com.raspingamazon.infrastructure.persistence.adapter;

import com.raspingamazon.application.deal.port.OfferEvidencePersistencePort;
import com.raspingamazon.application.enrichment.contract.ProductEnrichmentResult;
import com.raspingamazon.application.enrichment.port.OfferEvidenceRepository;
import com.raspingamazon.infrastructure.persistence.PersistenceOperationException;

import java.sql.SQLException;
import java.util.Objects;

/**
 * Adapter entre a nova porta do fluxo vertical e o repository
 * de evidências introduzido na FASE 8.5-B.
 *
 * <p>O repository mais antigo ainda declara SQLException.
 * O adapter mantém esse detalhe restrito à infraestrutura para que
 * AmazonDealProcessingService não dependa de JDBC.</p>
 */
public final class OfferEvidenceJdbcPersistenceAdapter
        implements OfferEvidencePersistencePort {

    private final OfferEvidenceRepository repository;

    public OfferEvidenceJdbcPersistenceAdapter(
            OfferEvidenceRepository repository
    ) {
        this.repository =
                Objects.requireNonNull(
                        repository,
                        "repository must not be null"
                );
    }

    @Override
    public void save(
            long offerSnapshotId,
            ProductEnrichmentResult enrichmentResult
    ) {
        if (offerSnapshotId <= 0) {
            throw new IllegalArgumentException(
                    "offerSnapshotId must be positive"
            );
        }

        Objects.requireNonNull(
                enrichmentResult,
                "enrichmentResult must not be null"
        );

        try {
            repository.insert(
                    offerSnapshotId,
                    enrichmentResult
            );

        } catch (SQLException exception) {

            throw new PersistenceOperationException(
                    "Failed to persist offer evidence for OfferSnapshot "
                            + offerSnapshotId,
                    exception
            );
        }
    }
}