package com.raspingamazon.application.deal.port;

import com.raspingamazon.application.deal.PersistedOfferSnapshot;
import com.raspingamazon.domain.deal.OfferSnapshot;

/**
 * Porta de persistência idempotente dos snapshots temporais.
 */
public interface OfferSnapshotPersistencePort {

    /**
     * Persiste uma observação quando ela ainda não existe.
     *
     * <p>A identidade lógica da observação é definida pela
     * infraestrutura persistente como:</p>
     *
     * <pre>
     * product
     * + collectedAt
     * + source
     * </pre>
     *
     * @param snapshot snapshot ainda sem identidade persistente
     * @return snapshot persistido e indicação de criação/reutilização
     */
    PersistedOfferSnapshot save(
            OfferSnapshot snapshot
    );
}