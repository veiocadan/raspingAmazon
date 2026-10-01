package com.raspingamazon.application.publication;

/**
 * Estado lógico de prontidão de uma ProcessingRun para entrar
 * na seleção/publicação automática.
 *
 * <p>Este estado é diferente de ProcessingRunStatus.</p>
 *
 * <p>ProcessingRunStatus descreve a coleta/parsing.</p>
 *
 * <p>ProcessingRunPublicationReadinessStatus descreve se toda a
 * linhagem necessária para seleção já produziu seus fatos
 * persistentes.</p>
 */
public enum ProcessingRunPublicationReadinessStatus {

    /**
     * Ainda existe trabalho durável capaz de produzir os fatos
     * necessários.
     */
    IN_PROGRESS,

    /**
     * Toda a linhagem necessária já possui fatos persistentes
     * suficientes para executar seleção/publicação.
     */
    READY,

    /**
     * A run não pode chegar a READY apenas esperando o trabalho
     * atualmente existente.
     *
     * <p>Exemplos:</p>
     *
     * <ul>
     *     <li>ProcessingRun FAILED;</li>
     *     <li>ENRICH_DEAL terminou definitivamente sem snapshot;</li>
     *     <li>EVALUATE_DEAL terminou definitivamente sem avaliação;</li>
     *     <li>falta estrutural de job que deveria existir.</li>
     * </ul>
     */
    BLOCKED
}
