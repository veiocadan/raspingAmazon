package com.raspingamazon.application.scheduling.port;

import com.raspingamazon.application.scheduling.ProcessingSchedule;
import com.raspingamazon.application.scheduling.ProcessingScheduleLease;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Porta de persistência e coordenação dos agendamentos recorrentes.
 *
 * <p>A aplicação conhece somente este contrato.</p>
 *
 * <p>PostgreSQL, SQL, locks concretos e transações pertencem
 * à infraestrutura.</p>
 */
public interface ProcessingSchedulePort {

    /**
     * Cria o agendamento quando ele ainda não existir.
     *
     * <p>Se o scheduleKey já estiver persistido, a implementação
     * deve preservar o estado operacional existente e devolver
     * o registro atual, em vez de sobrescrever silenciosamente
     * pause, frequência, próxima execução ou lease.</p>
     *
     * @param schedule configuração inicial
     * @return estado persistido
     */
    ProcessingSchedule saveIfAbsent(
        ProcessingSchedule schedule
    );

    /**
     * Consulta o estado atual de um agendamento.
     *
     * @param scheduleKey chave lógica
     * @return agendamento, quando existente
     */
    Optional<ProcessingSchedule> findByKey(
        String scheduleKey
    );

    /**
     * Tenta adquirir atomicamente uma janela vencida.
     *
     * <p>A aquisição somente deve ocorrer quando:</p>
     *
     * <ul>
     *     <li>o agendamento estiver habilitado;</li>
     *     <li>nextRunAt for menor ou igual a now;</li>
     *     <li>não existir lease válido pertencente a outra instância.</li>
     * </ul>
     *
     * <p>Duas instâncias concorrentes não podem adquirir a mesma
     * janela lógica.</p>
     *
     * @param scheduleKey chave do agendamento
     * @param leaseOwner identidade da instância
     * @param now instante atual
     * @param leaseDuration duração do lease
     * @return lease adquirido ou vazio
     */
    Optional<ProcessingScheduleLease> tryAcquireDue(
        String scheduleKey,
        String leaseOwner,
        OffsetDateTime now,
        Duration leaseDuration
    );

    /**
     * Confirma que a janela adquirida produziu uma ProcessingRun.
     *
     * <p>A operação deve validar ownership do lease.</p>
     *
     * <p>A confirmação deve:</p>
     *
     * <ul>
     *     <li>registrar scheduledFor;</li>
     *     <li>registrar processingRunId;</li>
     *     <li>atualizar nextRunAt;</li>
     *     <li>liberar o lease pertencente à instância.</li>
     * </ul>
     *
     * @param scheduleKey chave do agendamento
     * @param leaseOwner proprietário esperado
     * @param scheduledFor janela lógica processada
     * @param processingRunId run produzida
     * @param nextRunAt próxima execução
     * @param confirmedAt instante da confirmação
     * @return estado persistido atualizado
     */
    ProcessingSchedule confirmScheduled(
        String scheduleKey,
        String leaseOwner,
        OffsetDateTime scheduledFor,
        long processingRunId,
        OffsetDateTime nextRunAt,
        OffsetDateTime confirmedAt
    );

    /**
     * Libera um lease sem confirmar nova ProcessingRun.
     *
     * <p>Utilizado quando a tentativa falha antes da confirmação e
     * a janela deve permanecer recuperável.</p>
     *
     * @param scheduleKey chave do agendamento
     * @param leaseOwner proprietário esperado
     * @param releasedAt instante da liberação
     * @return estado persistido
     */
    ProcessingSchedule releaseLease(
        String scheduleKey,
        String leaseOwner,
        OffsetDateTime releasedAt
    );

    /**
     * Suspende a criação de novas execuções automáticas.
     *
     * <p>Não remove ProcessingRun ou ProcessingJob existentes.</p>
     *
     * @param scheduleKey chave do agendamento
     * @param changedAt instante da alteração
     * @return estado pausado
     */
    ProcessingSchedule pause(
        String scheduleKey,
        OffsetDateTime changedAt
    );

    /**
     * Retoma a criação de novas execuções automáticas.
     *
     * <p>O novo nextRunAt é explícito para que a aplicação possa
     * evitar catch-up ilimitado de janelas antigas.</p>
     *
     * @param scheduleKey chave do agendamento
     * @param nextRunAt próxima execução permitida
     * @param changedAt instante da alteração
     * @return estado retomado
     */
    ProcessingSchedule resume(
        String scheduleKey,
        OffsetDateTime nextRunAt,
        OffsetDateTime changedAt
    );

    /**
     * Altera a frequência configurada do agendamento.
     *
     * <p>O novo nextRunAt é fornecido explicitamente para evitar
     * que a camada de persistência possua regra temporal de negócio.</p>
     *
     * @param scheduleKey chave do agendamento
     * @param interval novo intervalo
     * @param nextRunAt próxima execução sob a nova frequência
     * @param changedAt instante da alteração
     * @return estado atualizado
     */
    ProcessingSchedule changeInterval(
        String scheduleKey,
        Duration interval,
        OffsetDateTime nextRunAt,
        OffsetDateTime changedAt
    );
}
