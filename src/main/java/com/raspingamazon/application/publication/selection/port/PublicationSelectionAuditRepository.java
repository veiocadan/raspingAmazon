package com.raspingamazon.application.publication.selection.port;

import com.raspingamazon.domain.publication.selection.PublicationSelectionResult;

/**
 * Porta de persistência da auditoria de seleção operacional
 * de publicações.
 *
 * <p>A aplicação conhece somente este contrato.</p>
 *
 * <p>A implementação concreta pertence à infraestrutura.</p>
 *
 * <p>O repository deve persistir o resultado produzido pelo
 * domínio sem recalcular score, histórico, recência, prioridade
 * ou decisão.</p>
 */
public interface PublicationSelectionAuditRepository {

    /**
     * Persiste uma execução completa da seleção e todas as
     * decisões pertencentes a ela.
     *
     * @param result resultado auditável produzido pelo domínio
     * @return identidade persistente da publication_selection_run
     */
    long save(
        PublicationSelectionResult result
    );
}
