package com.raspingamazon.application.publication.port;

import com.raspingamazon.application.publication.PublicationData;

import java.util.Optional;

/**
 * Porta de leitura dos dados necessários à geração de uma publicação.
 *
 * <p>A camada de aplicação conhece somente este contrato.
 * Ela não conhece JDBC, SQL nem a estratégia utilizada para
 * reconstruir os dados persistidos.</p>
 *
 * <p>A implementação concreta será responsabilidade da
 * infraestrutura.</p>
 */
public interface PublicationDataQueryPort {

    /**
     * Localiza os dados persistidos que originam uma publicação
     * a partir da identidade da DealEvaluation.
     *
     * @param dealEvaluationId identidade persistente da avaliação
     * @return os dados de publicação quando a avaliação existir
     */
    Optional<PublicationData> findByDealEvaluationId(
        long dealEvaluationId
    );
}
