package com.raspingamazon.application.operation.observability.alert.port;

import com.raspingamazon.application.operation.observability.alert.OperationalAlertPolicy;

/**
 * Porta responsável por fornecer a política operacional de alertas.
 *
 * <p>A aplicação conhece somente o contrato tipado. A origem concreta
 * da configuração pertence à infraestrutura.</p>
 *
 * <p>A política pode ser carregada no momento de cada avaliação.
 * Isso permite que a composição seja criada sem exigir configuração
 * de alertas para comandos operacionais que não utilizam alertas.</p>
 */
@FunctionalInterface
public interface OperationalAlertPolicyProvider {

    /**
     * Carrega a política que deve ser utilizada na avaliação atual.
     *
     * @return política operacional válida
     */
    OperationalAlertPolicy load();
}
