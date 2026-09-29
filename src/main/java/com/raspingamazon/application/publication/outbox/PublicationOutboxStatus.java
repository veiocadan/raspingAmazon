package com.raspingamazon.application.publication.outbox;

/**
 * Estado persistente de uma entrada da outbox de publicação.
 */
public enum PublicationOutboxStatus {

    /**
     * Reservada e aguardando worker.
     */
    PENDING,

    /**
     * Reivindicada temporariamente por um worker.
     */
    PROCESSING,

    /**
     * PublicationChannel confirmou sucesso.
     */
    SUCCEEDED,

    /**
     * PublicationChannel devolveu falha transitória.
     *
     * <p>Na FASE 18 este estado encerra a tentativa corrente.
     * Estratégias avançadas de retry pertencem à FASE 19.</p>
     */
    FAILED_TRANSIENT,

    /**
     * PublicationChannel devolveu falha permanente.
     */
    FAILED_PERMANENT
}
