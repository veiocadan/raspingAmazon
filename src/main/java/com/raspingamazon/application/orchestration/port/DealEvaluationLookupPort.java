package com.raspingamazon.application.orchestration.port;

import java.util.OptionalLong;

/**
 * Porta de leitura utilizada pela etapa EVALUATE_DEAL para
 * verificar se um OfferSnapshot já possui avaliação persistida.
 *
 * <p>Essa consulta permite que retries ocorridos depois de um
 * commit bem-sucedido não recalcularem filtros, score e momentum
 * desnecessariamente.</p>
 */
public interface DealEvaluationLookupPort {

    /**
     * Localiza a avaliação persistida para um OfferSnapshot.
     *
     * @param offerSnapshotId identidade do snapshot
     * @return id da avaliação quando já existir
     */
    OptionalLong findEvaluationIdByOfferSnapshotId(
        long offerSnapshotId
    );
}
