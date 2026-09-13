package com.raspingamazon.domain.publication;

/**
 * Estados possíveis do ciclo de vida de uma Publication.
 *
 * <p>Os estados representam somente o ciclo de vida da publicação
 * dentro do domínio. Detalhes de canais, provedores, filas e tentativas
 * individuais pertencem a outras camadas e não devem ser incorporados
 * neste enum.</p>
 *
 * <p>A especificação do projeto não fornecia originalmente um vocabulário
 * fechado para estes estados. Estes valores formalizam a decisão de domínio
 * adotada para o MVP da FASE 3.</p>
 */
public enum PublicationStatus {

    /**
     * Publicação criada e persistida, mas ainda não liberada para publicação.
     */
    CREATED,

    /**
     * Publicação validada e liberada para entrar no fluxo de publicação.
     */
    READY,

    /**
     * Publicação concluída com sucesso.
     */
    PUBLISHED,

    /**
     * Publicação que terminou com uma falha durante o processo.
     *
     * <p>Uma publicação neste estado poderá futuramente retornar para READY
     * quando uma política de retry determinar que o reprocessamento é seguro.</p>
     */
    FAILED
}