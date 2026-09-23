package com.raspingamazon.application.orchestration;

/**
 * Classificação operacional de falhas da orquestração.
 *
 * <p>Essa classificação determina se um trabalho pode voltar
 * automaticamente para a fila.</p>
 */
public enum ProcessingFailureType {

    /**
     * Falha potencialmente recuperável.
     *
     * <p>Exemplos futuros:</p>
     *
     * <ul>
     *     <li>timeout;</li>
     *     <li>indisponibilidade temporária;</li>
     *     <li>HTTP 5xx;</li>
     *     <li>falha transitória de banco.</li>
     * </ul>
     */
    TRANSIENT,

    /**
     * Falha que não deve ser repetida automaticamente com os mesmos
     * dados de entrada.
     *
     * <p>Exemplos futuros:</p>
     *
     * <ul>
     *     <li>entrada estruturalmente inválida;</li>
     *     <li>contrato impossível de interpretar;</li>
     *     <li>estado permanentemente incompatível.</li>
     * </ul>
     */
    PERMANENT
}
