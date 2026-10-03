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
     * PublicationChannel devolveu falha transitória e o trabalho não
     * possui mais retry automático disponível.
     *
     * <p>Retries ainda disponíveis reutilizam a mesma outbox e voltam
     * para PENDING.</p>
     */
    FAILED_TRANSIENT,

    /**
     * PublicationChannel devolveu falha permanente.
     */
    FAILED_PERMANENT,

    /**
     * O efeito externo da tentativa não pode ser determinado com
     * segurança.
     *
     * <p>Exemplos:</p>
     *
     * <ul>
     *     <li>crash após o início persistido da tentativa;</li>
     *     <li>provider possivelmente aceitou a mensagem, mas a
     *         confirmação local foi perdida;</li>
     *     <li>outro resultado ambíguo explicitamente classificado nas
     *         etapas seguintes da FASE 20.</li>
     * </ul>
     *
     * <p>Este estado é terminal para processamento automático. Qualquer
     * reprocessamento futuro deve ser uma decisão explícita e
     * auditável.</p>
     */
    DELIVERY_UNKNOWN
}
