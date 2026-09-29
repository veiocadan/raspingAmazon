package com.raspingamazon.application.publication.outbox;

/**
 * Resultado operacional da tentativa de reservar uma publicação
 * na outbox.
 */
public enum PublicationOutboxEnqueueStatus {

    /**
     * Uma nova linha foi criada na outbox e a vaga de quota
     * foi efetivamente reservada.
     */
    ENQUEUED,

    /**
     * A mesma identidade publication + channel + destination
     * já estava reservada.
     *
     * <p>Não consome uma segunda vaga.</p>
     */
    ALREADY_ENQUEUED,

    /**
     * Não havia capacidade disponível.
     *
     * <p>Nenhuma linha de outbox é criada e o candidato não
     * preserva prioridade para um ciclo futuro.</p>
     */
    QUOTA_EXHAUSTED,

    /**
     * A seleção deixou de ser válida para reserva.
     *
     * <p>Exemplos:</p>
     *
     * <ul>
     *     <li>mudança do perfil ativo de quota;</li>
     *     <li>virada do dia operacional;</li>
     *     <li>availableAt pertencente a outro dia de quota.</li>
     * </ul>
     *
     * <p>Nesse caso uma nova seleção deve ser realizada.</p>
     */
    STALE_SELECTION
}
