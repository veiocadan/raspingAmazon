package com.raspingamazon.application.deal.port;

import com.raspingamazon.domain.deal.OfferSnapshot;

/**
 * Porta de persistência dos snapshots temporais de ofertas.
 */
public interface OfferSnapshotPersistencePort {

    /**
     * Persiste um snapshot ainda sem id.
     *
     * @param snapshot snapshot construído pelo domínio/aplicação
     * @return nova representação com id persistente
     */
    OfferSnapshot save(OfferSnapshot snapshot);
}