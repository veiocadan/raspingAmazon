package com.raspingamazon.application.history;

import com.raspingamazon.domain.history.HistoricalOfferObservation;
import com.raspingamazon.domain.product.Asin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Porta de aplicação responsável pela leitura histórica de ofertas.
 *
 * <p>A aplicação conhece somente este contrato.</p>
 *
 * <p>A origem concreta do histórico pode ser PostgreSQL ou outra
 * implementação futura sem alterar o domínio consumidor.</p>
 *
 * <p>Os métodos específicos de primeira, última e observação anterior
 * evitam que consumidores precisem carregar todo o histórico apenas
 * para responder consultas pontuais.</p>
 */
public interface OfferHistoryQueryPort {

    /**
     * Retorna todo o histórico conhecido do ASIN.
     *
     * <p>A ordem é determinística:</p>
     *
     * <pre>
     * collectedAt ASC
     * snapshotId ASC
     * </pre>
     *
     * @param asin ASIN consultado
     * @return histórico em ordem cronológica
     */
    List<HistoricalOfferObservation> findHistoryByAsin(
        Asin asin
    );

    /**
     * Retorna a primeira observação histórica conhecida.
     *
     * @param asin ASIN consultado
     * @return primeira observação, quando existente
     */
    Optional<HistoricalOfferObservation> findFirstByAsin(
        Asin asin
    );

    /**
     * Retorna a observação histórica mais recente conhecida.
     *
     * @param asin ASIN consultado
     * @return última observação, quando existente
     */
    Optional<HistoricalOfferObservation> findLatestByAsin(
        Asin asin
    );

    /**
     * Localiza a observação imediatamente anterior a um instante.
     *
     * <p>A comparação temporal é estrita:</p>
     *
     * <pre>
     * previous.collectedAt < collectedAt
     * </pre>
     *
     * <p>Snapshots com o mesmo instante não são considerados
     * observações anteriores para cálculo temporal.</p>
     *
     * @param asin ASIN consultado
     * @param collectedAt limite temporal exclusivo
     * @return observação imediatamente anterior, quando existente
     */
    Optional<HistoricalOfferObservation> findPreviousByAsin(
        Asin asin,
        OffsetDateTime collectedAt
    );

    /**
     * Retorna a quantidade de observações históricas do ASIN.
     *
     * @param asin ASIN consultado
     * @return quantidade de snapshots
     */
    long countByAsin(
        Asin asin
    );
}
