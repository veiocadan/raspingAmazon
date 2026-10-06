package com.raspingamazon.application.orchestration.failure;

/**
 * Ação operacional associada à classificação de uma falha.
 *
 * <p>Uma mesma falha pode exigir mais de uma ação simultaneamente.
 * Por isso FailureClassification trabalha com um conjunto destas
 * ações, e não com uma única decisão exclusiva.</p>
 */
public enum FailureHandlingAction {

    /**
     * Uma nova tentativa automática é permitida pela política.
     */
    RETRY,

    /**
     * O trabalho atual deve ser encerrado sem nova tentativa
     * automática.
     */
    REJECT,

    /**
     * A execução da integração ou fluxo afetado deve ser interrompida
     * até que uma condição operacional seja resolvida.
     */
    PAUSE,

    /**
     * A falha merece sinalização operacional.
     */
    ALERT,

    /**
     * O trabalho poderá ser submetido novamente por um caso de uso
     * explícito de reprocessamento.
     *
     * <p>Esta ação não significa retry automático.</p>
     */
    REPROCESS,

    /**
     * A resolução depende de avaliação ou ação humana.
     */
    OPERATOR_INTERVENTION
}
