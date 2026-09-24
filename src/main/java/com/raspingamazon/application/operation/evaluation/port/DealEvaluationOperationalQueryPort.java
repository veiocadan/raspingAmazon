package com.raspingamazon.application.operation.evaluation.port;

import com.raspingamazon.application.operation.evaluation.DealEvaluationPage;
import com.raspingamazon.application.operation.evaluation.DealEvaluationSearchCriteria;

/**
 * Porta de leitura destinada à navegação operacional das avaliações
 * persistidas.
 *
 * <p>A implementação concreta pertence à infraestrutura.</p>
 *
 * <p>A consulta deve utilizar ordenação determinística:</p>
 *
 * <pre>
 * evaluatedAt DESC
 * evaluationId DESC
 * </pre>
 *
 * <p>Quando criteria.after estiver presente, a implementação deverá
 * retornar somente registros posteriores ao cursor dentro dessa
 * ordenação.</p>
 */
@FunctionalInterface
public interface DealEvaluationOperationalQueryPort {

    DealEvaluationPage search(
        DealEvaluationSearchCriteria criteria
    );
}
