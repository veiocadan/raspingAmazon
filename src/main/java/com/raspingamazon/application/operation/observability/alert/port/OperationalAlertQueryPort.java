package com.raspingamazon.application.operation.observability.alert.port;

import com.raspingamazon.application.operation.observability.alert.OperationalAlert;
import com.raspingamazon.application.operation.observability.alert.OperationalAlertPolicy;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Porta de leitura dos alertas operacionais ativos.
 *
 * <p>A implementação PostgreSQL futura poderá derivar os alertas
 * diretamente dos fatos já persistidos.</p>
 *
 * <p>Esta porta não agenda execução, não faz polling e não altera
 * estado operacional.</p>
 */
@FunctionalInterface
public interface OperationalAlertQueryPort {

    /**
     * Avalia os fatos persistidos no instante informado.
     *
     * @param policy política explícita de detecção
     * @param evaluatedAt instante civil da avaliação
     * @return alertas atualmente detectados
     */
    List<OperationalAlert> findActiveAlerts(
        OperationalAlertPolicy policy,
        OffsetDateTime evaluatedAt
    );
}
