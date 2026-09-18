package com.raspingamazon.application.evaluation;

import com.raspingamazon.domain.evaluation.DealEvaluation;

/**
 * Contrato de persistência das avaliações de ofertas.
 *
 * <p>A camada de aplicação conhece somente este contrato.
 * A implementação concreta pertence à infraestrutura.</p>
 */
public interface DealEvaluationRepository {

    /**
     * Persiste uma avaliação.
     *
     * @param evaluation avaliação produzida pelas regras de negócio
     * @return avaliação persistida
     */
    DealEvaluation save(DealEvaluation evaluation);
}