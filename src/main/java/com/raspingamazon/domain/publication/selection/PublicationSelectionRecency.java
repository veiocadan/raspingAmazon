package com.raspingamazon.domain.publication.selection;

/**
 * Classificação temporal de um candidato no instante da seleção.
 *
 * <p>Esta classificação explica como o histórico de publicação
 * influenciou a prioridade operacional.</p>
 */
public enum PublicationSelectionRecency {

    /**
     * Nenhuma publicação externa bem-sucedida existe no escopo.
     */
    NEVER_SUCCESSFULLY_PUBLISHED,

    /**
     * Existe histórico, mas o último sucesso já está fora da
     * janela preferencial de recorrência.
     */
    OUTSIDE_PREFERRED_COOLDOWN,

    /**
     * O candidato já saiu do hard cooldown, mas ainda está dentro
     * da janela preferencial de recorrência.
     */
    INSIDE_PREFERRED_COOLDOWN,

    /**
     * O candidato ainda está proibido temporariamente de participar
     * da seleção.
     */
    INSIDE_HARD_COOLDOWN
}
