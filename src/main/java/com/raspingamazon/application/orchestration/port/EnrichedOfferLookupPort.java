package com.raspingamazon.application.orchestration.port;

import com.raspingamazon.application.orchestration.DealCandidate;

import java.util.OptionalLong;

/**
 * Porta de leitura que permite descobrir se um DealCandidate já
 * produziu um OfferSnapshot persistido.
 *
 * <p>Essa consulta protege a etapa ENRICH_DEAL contra repetição
 * desnecessária de chamadas externas após uma execução anterior
 * já ter sido persistida com sucesso.</p>
 */
public interface EnrichedOfferLookupPort {

    /**
     * Localiza o snapshot correspondente à identidade lógica
     * da oferta representada pelo candidato.
     *
     * <p>A identidade utilizada é:</p>
     *
     * <pre>
     * ASIN
     * + collectedAt
     * + source
     * </pre>
     *
     * @param candidate candidato persistido
     * @return id do snapshot, quando já existir
     */
    OptionalLong findSnapshotId(
        DealCandidate candidate
    );
}
