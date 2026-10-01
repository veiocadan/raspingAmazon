package com.raspingamazon.application.publication.selection.port;

import com.raspingamazon.application.publication.selection.PublicationSelectionSourceCandidate;

import java.util.List;

/**
 * Fonte persistente de candidatos já avaliados e pontuados que
 * pertencem a uma ProcessingRun.
 *
 * <p>Esta porta não executa:</p>
 *
 * <ul>
 *     <li>coleta;</li>
 *     <li>enrichment;</li>
 *     <li>elegibilidade;</li>
 *     <li>filtros comerciais;</li>
 *     <li>score;</li>
 *     <li>ranking;</li>
 *     <li>seleção operacional;</li>
 *     <li>publicação.</li>
 * </ul>
 *
 * <p>Ela apenas reconstrói, a partir da linhagem persistida:</p>
 *
 * <pre>
 * ProcessingRun
 *      |
 *      v
 * DealCandidate
 *      |
 *      v
 * OfferSnapshot
 *      |
 *      v
 * DealEvaluation
 * </pre>
 *
 * <p>Somente DealEvaluations elegíveis e efetivamente pontuadas
 * são candidatas à seleção operacional.</p>
 */
@FunctionalInterface
public interface PublicationSelectionSourceQueryPort {

    /**
     * Retorna os candidatos elegíveis e pontuados pertencentes à
     * ProcessingRun informada.
     *
     * @param processingRunId identidade persistente da ProcessingRun
     * @return candidatos aptos à seleção; nunca null
     */
    List<PublicationSelectionSourceCandidate>
    findEligibleScoredByProcessingRunId(
        long processingRunId
    );
}
