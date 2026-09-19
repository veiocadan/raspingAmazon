package com.raspingamazon.application.deal.port;

import com.raspingamazon.domain.commercial.PaymentCondition;

import java.util.List;

/**
 * Porta responsável pela persistência das condições comerciais
 * ligadas a um OfferSnapshot.
 */
public interface PaymentConditionPersistencePort {

    /**
     * Persiste todas as condições comerciais de um snapshot.
     *
     * <p>Uma lista vazia é válida e representa ausência de
     * condições comerciais observadas.</p>
     *
     * @param offerSnapshotId id do snapshot persistido
     * @param conditions condições comerciais observadas
     */
    void saveAll(
            long offerSnapshotId,
            List<PaymentCondition> conditions
    );
}