package com.raspingamazon.application.publication.port;

import java.util.List;

/**
 * Consulta ProcessingRuns que ainda precisam ser reconciliadas
 * com a etapa durável PUBLICATION_DISPATCH.
 *
 * <p>A porta retorna somente identidades persistentes.</p>
 *
 * <p>A decisão READY / IN_PROGRESS / BLOCKED permanece pertencendo
 * ao contrato de readiness da publicação.</p>
 */
@FunctionalInterface
public interface PublicationDispatchReconciliationCandidateQueryPort {

    /**
     * Retorna até {@code limit} ProcessingRuns candidatas à
     * reconciliação.
     *
     * <p>A implementação deve utilizar ordenação determinística.</p>
     *
     * @param limit quantidade máxima de runs
     * @return ids das ProcessingRuns candidatas
     */
    List<Long> findCandidates(
        int limit
    );
}
