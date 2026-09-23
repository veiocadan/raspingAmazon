package com.raspingamazon.application.orchestration;

/**
 * Estado de uma execução lógica de coleta.
 *
 * <p>ProcessingRun representa a execução que originou candidatos.
 * Os jobs derivados possuem ciclo de vida próprio.</p>
 */
public enum ProcessingRunStatus {

    /**
     * Execução registrada, mas ainda não iniciada.
     */
    PENDING,

    /**
     * Coleta/parsing atualmente em execução.
     */
    RUNNING,

    /**
     * Coleta/parsing concluídos com sucesso.
     */
    COMPLETED,

    /**
     * Coleta/parsing encerrados com falha.
     */
    FAILED
}
