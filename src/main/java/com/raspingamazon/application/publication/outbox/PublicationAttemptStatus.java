package com.raspingamazon.application.publication.outbox;

/**
 * Lifecycle persistente de uma tentativa física de entrega.
 *
 * <p>Este estado pertence à tentativa externa, não à política de retry
 * da outbox.</p>
 *
 * <p>A distinção entre STARTED e DELIVERY_UNKNOWN é essencial para
 * recuperação segura após crash:</p>
 *
 * <pre>
 * STARTED
 *     provider pode já ter recebido a requisição
 *     mas ainda não existe resultado persistido
 *
 * DELIVERY_UNKNOWN
 *     o sistema perdeu a capacidade de provar
 *     se o efeito externo ocorreu ou não
 * </pre>
 */
public enum PublicationAttemptStatus {

    /**
     * O início da tentativa foi persistido antes da chamada externa.
     *
     * <p>Enquanto este estado estiver ativo, finishedAt permanece
     * ausente.</p>
     */
    STARTED,

    /**
     * O provider confirmou a entrega.
     */
    SUCCESS,

    /**
     * O provider confirmou uma falha classificada como transitória.
     */
    FAILED_TRANSIENT,

    /**
     * O provider confirmou uma falha permanente.
     */
    FAILED_PERMANENT,

    /**
     * A chamada externa pode ter produzido efeito, mas o sistema não
     * possui confirmação durável suficiente para classificá-la como
     * sucesso ou falha.
     *
     * <p>Este estado nunca autoriza retry automático por si só.</p>
     */
    DELIVERY_UNKNOWN
}
