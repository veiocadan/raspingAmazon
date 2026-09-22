package com.raspingamazon.application.orchestration.port;

import com.raspingamazon.application.orchestration.ProcessingFailure;
import com.raspingamazon.application.orchestration.ProcessingJob;
import com.raspingamazon.application.orchestration.ProcessingJobSubmission;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Porta da aplicação para a fila durável de processamento.
 *
 * <p>A aplicação não conhece PostgreSQL, SELECT FOR UPDATE,
 * SKIP LOCKED ou qualquer outra tecnologia utilizada pela
 * implementação concreta.</p>
 */
public interface ProcessingJobQueuePort {

    /**
     * Registra uma unidade lógica de trabalho de maneira idempotente.
     *
     * <p>A identidade lógica é definida por:</p>
     *
     * <pre>
     * jobType + idempotencyKey
     * </pre>
     *
     * <p>Quando o mesmo trabalho já existir, a implementação deve
     * retornar o job existente em vez de criar duplicata.</p>
     *
     * @param submission solicitação de criação do job
     * @return job persistido, novo ou previamente existente
     */
    ProcessingJob enqueue(
        ProcessingJobSubmission submission
    );

    /**
     * Reivindica atomicamente o próximo job disponível.
     *
     * <p>A implementação deve garantir que dois workers concorrentes
     * nunca obtenham simultaneamente o mesmo job.</p>
     *
     * <p>Ao reivindicar o job, a implementação deve:</p>
     *
     * <ul>
     *     <li>alterar o estado para RUNNING;</li>
     *     <li>registrar lockedAt;</li>
     *     <li>registrar lockedBy;</li>
     *     <li>incrementar attemptCount;</li>
     *     <li>respeitar availableAt.</li>
     * </ul>
     *
     * @param workerId identidade do worker
     * @param claimedAt instante da reivindicação
     * @return job reivindicado ou vazio quando nada estiver disponível
     */
    Optional<ProcessingJob> claimNext(
        String workerId,
        OffsetDateTime claimedAt
    );

    /**
     * Marca como concluído um job pertencente ao worker informado.
     *
     * <p>A implementação deve remover o lock operacional e registrar
     * finishedAt.</p>
     *
     * @param jobId identidade persistente do job
     * @param workerId worker atualmente proprietário do job
     * @param finishedAt instante da conclusão
     * @return estado persistido após a conclusão
     */
    ProcessingJob markSucceeded(
        long jobId,
        String workerId,
        OffsetDateTime finishedAt
    );

    /**
     * Agenda nova tentativa de uma falha transitória.
     *
     * <p>Somente falhas classificadas como TRANSIENT podem utilizar
     * esta operação.</p>
     *
     * <p>O job deixa RUNNING, perde o lock e passa para RETRY_WAIT.</p>
     *
     * @param jobId identidade persistente do job
     * @param workerId worker atualmente proprietário do job
     * @param failure falha transitória
     * @param availableAt instante mínimo da próxima tentativa
     * @param failedAt instante em que a tentativa falhou
     * @return estado persistido após o reagendamento
     */
    ProcessingJob scheduleRetry(
        long jobId,
        String workerId,
        ProcessingFailure failure,
        OffsetDateTime availableAt,
        OffsetDateTime failedAt
    );

    /**
     * Encerra definitivamente um job.
     *
     * <p>Esta operação será utilizada para falhas permanentes e
     * também para falhas transitórias que tenham esgotado o número
     * máximo de tentativas.</p>
     *
     * <p>O job deixa RUNNING, perde o lock e passa para DEAD.</p>
     *
     * @param jobId identidade persistente do job
     * @param workerId worker atualmente proprietário do job
     * @param failure causa do encerramento
     * @param failedAt instante do encerramento
     * @return estado persistido após o encerramento
     */
    ProcessingJob markDead(
        long jobId,
        String workerId,
        ProcessingFailure failure,
        OffsetDateTime failedAt
    );
}
