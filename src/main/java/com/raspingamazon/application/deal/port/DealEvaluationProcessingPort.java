package com.raspingamazon.application.deal.port;

import com.raspingamazon.domain.deal.OfferSnapshot;

import java.time.OffsetDateTime;

/**
 * Porta utilizada pelo fluxo vertical para solicitar a avaliação
 * de um OfferSnapshot já persistido.
 *
 * <p>A implementação concreta poderá utilizar
 * AmazonDealEvaluationApplicationService, mas o orquestrador
 * não precisa conhecer esse detalhe.</p>
 */
public interface DealEvaluationProcessingPort {

    /**
     * Avalia e persiste o resultado da avaliação do snapshot.
     *
     * @param offerSnapshot snapshot persistido
     * @param evaluatedAt instante da avaliação
     */
    void evaluateAndPersist(
            OfferSnapshot offerSnapshot,
            OffsetDateTime evaluatedAt
    );
}