package com.raspingamazon.application.momentum;

import com.raspingamazon.domain.momentum.MomentumAudit;

/**
 * Porta de persistência da trilha auditável de momentum.
 *
 * <p>A aplicação conhece somente este contrato.</p>
 *
 * <p>A implementação concreta pertence à infraestrutura.</p>
 */
public interface MomentumAuditRepository {

    /**
     * Persiste uma nova auditoria de momentum.
     *
     * @param audit auditoria ainda sem identidade persistente
     * @return auditoria com identidade persistente
     */
    MomentumAudit save(
        MomentumAudit audit
    );
}
