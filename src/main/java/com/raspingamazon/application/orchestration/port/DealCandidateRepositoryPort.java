package com.raspingamazon.application.orchestration.port;

import com.raspingamazon.application.orchestration.DealCandidate;

import java.util.Optional;

/**
 * Porta de persistência dos candidatos produzidos pelo parser.
 *
 * <p>DealCandidate é a fronteira durável entre:</p>
 *
 * <pre>
 * coleta + parsing
 *       ↓
 * enriquecimento
 * </pre>
 *
 * <p>Uma falha posterior não exige repetir a coleta original.</p>
 */
public interface DealCandidateRepositoryPort {

    /**
     * Persiste um candidato de forma idempotente.
     *
     * <p>A identidade persistente é protegida pela combinação:</p>
     *
     * <pre>
     * processingRunId
     * + asin
     * + collectedAt
     * + source
     * </pre>
     *
     * @param candidate candidato ainda sem identidade persistente
     * @return candidato persistido, novo ou previamente existente
     */
    DealCandidate save(
        DealCandidate candidate
    );

    /**
     * Localiza um candidato pela identidade persistente.
     *
     * @param id identidade persistente
     * @return candidato quando encontrado
     */
    Optional<DealCandidate> findById(
        long id
    );
}
