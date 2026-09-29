package com.raspingamazon.application.publication.selection.port;

import java.time.LocalDate;

/**
 * Porta de leitura da ocupação persistente da quota de publicação.
 *
 * <p>A implementação deve considerar somente trabalho que realmente
 * ocupa ou reserva capacidade segundo o contrato persistente da
 * publicação.</p>
 *
 * <p>A auditoria de seleção não constitui reserva de quota e não
 * deve ser utilizada como fonte desta contagem.</p>
 *
 * <p>A implementação PostgreSQL será introduzida junto da outbox,
 * quando existir uma fonte persistente capaz de representar
 * corretamente trabalho reservado, pendente e concluído.</p>
 */
public interface PublicationQuotaUsageQueryPort {

    /**
     * Retorna quantas posições da quota estão ocupadas no escopo
     * e dia informados.
     *
     * @param channel canal lógico
     * @param destination destino dentro do canal
     * @param quotaDate dia operacional da quota
     * @return quantidade persistente de posições ocupadas
     */
    long occupiedSlots(
        String channel,
        String destination,
        LocalDate quotaDate
    );
}
