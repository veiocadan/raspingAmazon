package com.raspingamazon.application.orchestration;

/**
 * Etapas duráveis do pipeline assíncrono.
 *
 * <p>Cada tipo representa uma unidade de trabalho que pode ser
 * persistida, reivindicada por um worker e reexecutada
 * independentemente das demais etapas.</p>
 *
 * <p>Publicação deliberadamente não pertence à FASE 12.
 * Essa responsabilidade permanece reservada à fase de publicação.</p>
 */
public enum ProcessingJobType {

    /**
     * Executa a coleta da fonte e o parsing das ofertas encontradas.
     */
    COLLECT_DEALS,

    /**
     * Enriquece um DealCandidate já persistido.
     */
    ENRICH_DEAL,

    /**
     * Avalia um OfferSnapshot já persistido.
     */
    EVALUATE_DEAL
}
