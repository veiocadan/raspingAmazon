package com.raspingamazon.application.orchestration.port;

import com.raspingamazon.application.orchestration.ProcessingRun;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Porta de persistência das execuções lógicas de coleta.
 *
 * <p>A aplicação conhece somente este contrato.</p>
 *
 * <p>Detalhes como PostgreSQL, JDBC e SQL pertencem
 * exclusivamente à infraestrutura.</p>
 */
public interface ProcessingRunRepositoryPort {

    /**
     * Persiste uma nova ProcessingRun de maneira idempotente.
     *
     * <p>A identidade lógica da run é definida por runKey.</p>
     *
     * <p>Quando a mesma runKey já existir, a implementação deve
     * retornar a execução já persistida em vez de criar duplicata.</p>
     *
     * @param run execução ainda sem identidade persistente
     * @return execução persistida
     */
    ProcessingRun save(
        ProcessingRun run
    );

    /**
     * Localiza uma execução pela identidade persistente.
     *
     * @param id identidade persistente
     * @return execução quando encontrada
     */
    Optional<ProcessingRun> findById(
        long id
    );

    /**
     * Marca uma execução como iniciada.
     *
     * <p>Uma execução anteriormente FAILED pode voltar para RUNNING
     * quando o job de coleta for reprocessado.</p>
     *
     * @param id identidade persistente
     * @param startedAt instante de início da tentativa atual
     * @return estado persistido
     */
    ProcessingRun markRunning(
        long id,
        OffsetDateTime startedAt
    );

    /**
     * Marca a coleta/parsing como concluídos.
     *
     * @param id identidade persistente
     * @param completedAt instante da conclusão
     * @return estado persistido
     */
    ProcessingRun markCompleted(
        long id,
        OffsetDateTime completedAt
    );

    /**
     * Registra falha da coleta/parsing.
     *
     * <p>O estado FAILED da run não determina sozinho se haverá
     * retry. Essa decisão pertence ao ProcessingJob.</p>
     *
     * @param id identidade persistente
     * @param errorCode código estruturado da falha
     * @param errorMessage descrição opcional
     * @param failedAt instante da falha
     * @return estado persistido
     */
    ProcessingRun markFailed(
        long id,
        String errorCode,
        String errorMessage,
        OffsetDateTime failedAt
    );
}
