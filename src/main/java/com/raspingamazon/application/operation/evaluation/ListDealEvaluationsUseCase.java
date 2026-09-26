package com.raspingamazon.application.operation.evaluation;

import com.raspingamazon.application.operation.evaluation.port.DealEvaluationOperationalQueryPort;

import java.util.Objects;

/**
 * Caso de uso de consulta da listagem operacional de avaliações.
 *
 * <p>A camada de apresentação depende deste caso de uso e não conhece
 * JDBC, SQL ou detalhes do PostgreSQL.</p>
 */
public final class ListDealEvaluationsUseCase {

    private final DealEvaluationOperationalQueryPort queryPort;

    public ListDealEvaluationsUseCase(
        DealEvaluationOperationalQueryPort queryPort
    ) {

        this.queryPort =
            Objects.requireNonNull(
                queryPort,
                "ListDealEvaluationsUseCase queryPort must not be null"
            );
    }

    public DealEvaluationPage execute(
        DealEvaluationSearchCriteria criteria
    ) {

        Objects.requireNonNull(
            criteria,
            "ListDealEvaluationsUseCase criteria must not be null"
        );

        return Objects.requireNonNull(
            queryPort.search(
                criteria
            ),
            "DealEvaluationOperationalQueryPort "
                + "must not return null"
        );
    }
}
