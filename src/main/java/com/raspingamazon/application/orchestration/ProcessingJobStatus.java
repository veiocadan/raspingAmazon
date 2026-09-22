package com.raspingamazon.application.orchestration;

/**
 * Estado operacional de um ProcessingJob.
 *
 * <p>Os estados representam somente execução técnica.
 * Eles não representam elegibilidade comercial da oferta.</p>
 */
public enum ProcessingJobStatus {

    /**
     * Job disponível para processamento.
     */
    PENDING,

    /**
     * Job atualmente reivindicado por um worker.
     */
    RUNNING,

    /**
     * Job que falhou de maneira transitória e aguarda nova tentativa.
     */
    RETRY_WAIT,

    /**
     * Job concluído com sucesso.
     */
    SUCCEEDED,

    /**
     * Job encerrado sem novas tentativas.
     */
    DEAD
}
