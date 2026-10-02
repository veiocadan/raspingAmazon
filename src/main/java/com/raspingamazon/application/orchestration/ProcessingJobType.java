package com.raspingamazon.application.orchestration;

/**
 * Etapas duráveis do pipeline assíncrono.
 *
 * <p>Cada tipo representa uma unidade de trabalho que pode ser
 * persistida, reivindicada por um worker e reexecutada
 * independentemente das demais etapas.</p>
 *
 * <p>As três primeiras etapas pertencem ao processamento técnico
 * iniciado na FASE 12.</p>
 *
 * <p>PUBLICATION_DISPATCH pertence à evolução de publicação automática
 * da FASE 19 e utiliza a ProcessingRun como raiz da seleção e do
 * despacho de publicações.</p>
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
    EVALUATE_DEAL,

    /**
     * Executa a etapa automática de seleção e despacho de publicações
     * associadas a uma ProcessingRun.
     *
     * <p>Este job é durável e utiliza processingRunId como sujeito.</p>
     *
     * <p>A existência do tipo não implica aprovação humana.</p>
     */
    PUBLICATION_DISPATCH
}
