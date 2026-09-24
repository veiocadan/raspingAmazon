package com.raspingamazon.application.operation.evaluation;

import com.raspingamazon.application.operation.evaluation.port.DealEvaluationOperationalDetailQueryPort;

import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso para obtenção do detalhe operacional de uma avaliação.
 */
public final class GetDealEvaluationDetailUseCase {

    private final DealEvaluationOperationalDetailQueryPort queryPort;

    public GetDealEvaluationDetailUseCase(
        DealEvaluationOperationalDetailQueryPort queryPort
    ) {

        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "GetDealEvaluationDetailUseCase "
                    + "queryPort must not be null"
            );
    }

    public Optional<DealEvaluationDetail> execute(
        long evaluationId
    ) {

        if (evaluationId <= 0L) {
            throw new IllegalArgumentException(
                "evaluationId must be positive"
            );
        }

        return Objects.requireNonNull(
            queryPort.findById(
                evaluationId
            ),
            "DealEvaluationOperationalDetailQueryPort "
                + "must not return null"
        );
    }
}
