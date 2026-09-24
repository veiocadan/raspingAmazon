package com.raspingamazon.application.operation.evaluation.port;

import com.raspingamazon.application.operation.evaluation.DealEvaluationDetail;

import java.util.Optional;

/**
 * Porta de leitura do detalhe operacional de uma DealEvaluation.
 */
@FunctionalInterface
public interface DealEvaluationOperationalDetailQueryPort {

    Optional<DealEvaluationDetail> findById(
        long evaluationId
    );
}
