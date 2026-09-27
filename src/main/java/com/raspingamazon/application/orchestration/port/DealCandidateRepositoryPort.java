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
     * + source
     * </pre>
     *
     * <p>collectedAt é preservado como fato da primeira observação,
     * mas não participa da identidade idempotente do candidato.</p>
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

    /**
     * Registra a correlação persistente entre um DealCandidate e o
     * OfferSnapshot produzido ou reutilizado durante o enrichment.
     *
     * <p>A operação deve ser idempotente para a mesma associação:</p>
     *
     * <pre>
     * candidate A -> snapshot X
     * candidate A -> snapshot X
     * </pre>
     *
     * <p>Entretanto, uma associação já estabelecida não pode ser
     * silenciosamente substituída:</p>
     *
     * <pre>
     * candidate A -> snapshot X
     *
     * seguido de:
     *
     * candidate A -> snapshot Y
     *
     * deve falhar.
     * </pre>
     *
     * @param dealCandidateId identidade persistente do candidato
     * @param offerSnapshotId identidade persistente do snapshot
     */
    void linkOfferSnapshot(
        long dealCandidateId,
        long offerSnapshotId
    );
}
