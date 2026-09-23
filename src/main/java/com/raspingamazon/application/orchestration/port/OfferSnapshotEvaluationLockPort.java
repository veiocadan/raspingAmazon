package com.raspingamazon.application.orchestration.port;

/**
 * Serializa avaliações concorrentes do mesmo OfferSnapshot.
 *
 * <p>O lock deve permanecer ativo até o término da transação
 * que executa a avaliação.</p>
 */
@FunctionalInterface
public interface OfferSnapshotEvaluationLockPort {

    /**
     * Adquire lock exclusivo sobre o OfferSnapshot persistido.
     *
     * @param offerSnapshotId identidade do snapshot
     */
    void lockById(
        long offerSnapshotId
    );
}
