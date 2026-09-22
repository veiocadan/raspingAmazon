package com.raspingamazon.application.orchestration.port;

import com.raspingamazon.domain.deal.OfferSnapshot;

import java.util.Optional;

/**
 * Porta de leitura utilizada pela etapa EVALUATE_DEAL.
 *
 * <p>A avaliação assíncrona pode executar em outro processo,
 * worker ou momento. Portanto, ela não pode depender do
 * OfferSnapshot que permaneceu em memória durante o enrichment.</p>
 *
 * <p>A implementação deve reconstruir o agregado necessário para
 * avaliação a partir da persistência durável, incluindo:</p>
 *
 * <ul>
 *     <li>Product;</li>
 *     <li>dados temporais e comerciais do OfferSnapshot;</li>
 *     <li>SellerType normalizado;</li>
 *     <li>DeliveryType normalizado;</li>
 *     <li>PaymentConditions e respectivos métodos.</li>
 * </ul>
 */
public interface OfferSnapshotEvaluationLoadPort {

    /**
     * Reconstrói um OfferSnapshot persistido completo.
     *
     * @param offerSnapshotId identidade persistente
     * @return snapshot quando encontrado
     */
    Optional<OfferSnapshot> findById(
        long offerSnapshotId
    );
}
